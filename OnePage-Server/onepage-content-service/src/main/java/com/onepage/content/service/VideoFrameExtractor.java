package com.onepage.content.service;

import com.onepage.content.entity.MediaAsset;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 从笔记视频抽出 JPEG 临时放到 MinIO，给看图模型按帧审。
 * 解码走 Maven 自带的 FFmpeg 原生库，不用本机安装 ffmpeg。
 */
@Component
public class VideoFrameExtractor {
  private static final Logger log = LoggerFactory.getLogger(VideoFrameExtractor.class);
  private static final String PREFIX = "review_frame/";
  private static final int FRAME_MAX = 6;
  private static final int WIDTH_MAX = 640;
  private static final double INTERVAL_SEC = 5D;

  private final MinioClient minioClient;
  private final String bucket;
  private final String mediaPublicBase;

  public VideoFrameExtractor(
      MinioClient minioClient,
      @Value("${onepage.minio.bucket}") String bucket,
      @Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.minioClient = minioClient;
    this.bucket = bucket;
    this.mediaPublicBase = mediaPublicBase == null ? "" : mediaPublicBase.replaceAll("/+$", "");
  }

  public List<String> extractAndPublish(MediaAsset asset) {
    if (asset == null || !StringUtils.hasText(asset.getObjectKey())
        || !StringUtils.hasText(mediaPublicBase)) {
      return List.of();
    }
    Path dir = null;
    try {
      dir = Files.createTempDirectory("onepage-vframe-");
      Path video = dir.resolve("src.bin");
      try (InputStream in = minioClient.getObject(
          GetObjectArgs.builder().bucket(bucket).object(asset.getObjectKey()).build())) {
        Files.copy(in, video);
      }
      if (Files.size(video) < 64L) {
        return List.of();
      }
      List<byte[]> jpegs = grab(video);
      List<String> urls = new ArrayList<>();
      for (int i = 0; i < jpegs.size() && urls.size() < FRAME_MAX; i++) {
        String key = PREFIX + asset.getId() + "/" + i + ".jpg";
        byte[] body = jpegs.get(i);
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .contentType("image/jpeg")
                .stream(new ByteArrayInputStream(body), body.length, -1)
                .build());
        urls.add(mediaPublicBase + "/" + key);
      }
      return List.copyOf(urls);
    } catch (Exception ex) {
      log.warn("video frame extract {} failed", asset.getId(), ex);
      return List.of();
    } finally {
      deleteDir(dir);
    }
  }

  public void deletePublished(List<String> urls) {
    if (urls == null || urls.isEmpty() || !StringUtils.hasText(mediaPublicBase)) {
      return;
    }
    String prefix = mediaPublicBase + "/";
    for (String url : urls) {
      if (url == null || !url.startsWith(prefix)) {
        continue;
      }
      String key = url.substring(prefix.length());
      if (!key.startsWith(PREFIX)) {
        continue;
      }
      try {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
      } catch (Exception ex) {
        log.warn("delete review frame failed");
      }
    }
  }

  private List<byte[]> grab(Path video) throws Exception {
    FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile());
    Java2DFrameConverter converter = new Java2DFrameConverter();
    try {
      grabber.start();
      long lengthUs = Math.max(0L, grabber.getLengthInTime());
      int count = sampleCount(lengthUs);
      List<byte[]> out = new ArrayList<>();
      if (lengthUs > 0L) {
        for (long timestamp : timestamps(lengthUs, count)) {
          if (out.size() >= FRAME_MAX) {
            break;
          }
          try {
            grabber.setTimestamp(timestamp);
          } catch (Exception ex) {
            continue;
          }
          byte[] jpeg = nextJpeg(grabber, converter);
          if (jpeg.length > 32) {
            out.add(jpeg);
          }
        }
      }
      if (out.isEmpty()) {
        out.addAll(grabSequential(grabber, converter));
      }
      return out;
    } finally {
      converter.close();
      try {
        grabber.stop();
      } catch (Exception ignored) {
        // 关闭失败不挡机审
      }
      try {
        grabber.release();
      } catch (Exception ignored) {
        // 同上
      }
    }
  }

  private static List<byte[]> grabSequential(FFmpegFrameGrabber grabber, Java2DFrameConverter converter) {
    List<byte[]> out = new ArrayList<>();
    int frames = Math.max(0, grabber.getLengthInFrames());
    int stride = frames > FRAME_MAX ? Math.max(1, frames / FRAME_MAX) : 1;
    int seen = 0;
    while (out.size() < FRAME_MAX) {
      byte[] jpeg = nextJpeg(grabber, converter);
      if (jpeg.length <= 32) {
        break;
      }
      if (seen % stride == 0) {
        out.add(jpeg);
      }
      seen++;
    }
    return out;
  }

  private static byte[] nextJpeg(FFmpegFrameGrabber grabber, Java2DFrameConverter converter) {
    try {
      Frame frame = grabber.grabImage();
      if (frame == null || frame.image == null) {
        return new byte[0];
      }
      BufferedImage image = converter.convert(frame);
      if (image == null) {
        return new byte[0];
      }
      return toJpeg(image);
    } catch (Exception ex) {
      return new byte[0];
    }
  }

  private static int sampleCount(long lengthUs) {
    double durationSec = lengthUs / 1_000_000D;
    if (durationSec <= 0D) {
      return 1;
    }
    int byInterval = (int) Math.floor(durationSec / INTERVAL_SEC) + 1;
    return Math.min(FRAME_MAX, Math.max(1, byInterval));
  }

  private static long[] timestamps(long lengthUs, int count) {
    if (count <= 1) {
      return new long[] {0L};
    }
    long last = Math.max(0L, lengthUs - 100_000L);
    long[] times = new long[count];
    for (int i = 0; i < count; i++) {
      times[i] = i * last / (count - 1);
    }
    return times;
  }

  private static byte[] toJpeg(BufferedImage source) throws Exception {
    BufferedImage image = source;
    if (source.getWidth() > WIDTH_MAX && source.getWidth() > 0) {
      int height = Math.max(1, source.getHeight() * WIDTH_MAX / source.getWidth());
      image = new BufferedImage(WIDTH_MAX, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = image.createGraphics();
      graphics.drawImage(source, 0, 0, WIDTH_MAX, height, null);
      graphics.dispose();
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", buffer);
    return buffer.toByteArray();
  }

  private static void deleteDir(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (Exception ignored) {
          // 临时目录清不掉不挡机审
        }
      });
    } catch (Exception ignored) {
      // 同上
    }
  }
}
