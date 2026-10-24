package com.onepage.ai.service;

import com.onepage.ai.AiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** 只拉本站媒体公网地址，转成 data URL 给看图模型。上游读不到 127.0.0.1 MinIO。 */
@Component
public class MediaImageLoader {
  private static final Logger log = LoggerFactory.getLogger(MediaImageLoader.class);

  private final String mediaPublicBase;

  public MediaImageLoader(@Value("${onepage.media-public-base}") String mediaPublicBase) {
    this.mediaPublicBase = mediaPublicBase == null ? "" : mediaPublicBase.trim().replaceAll("/+$", "");
  }

  public List<String> toDataUrls(List<String> urls) {
    return toDataUrls(urls, AiConstants.CHAT_IMAGE_MAX);
  }

  public List<String> toDataUrls(List<String> urls, int max) {
    if (urls == null || urls.isEmpty() || !StringUtils.hasText(mediaPublicBase)) {
      return List.of();
    }
    int cap = max < 1 ? 1 : max;
    List<String> out = new ArrayList<>();
    for (String raw : urls) {
      if (out.size() >= cap) {
        break;
      }
      String dataUrl = loadOne(raw);
      if (StringUtils.hasText(dataUrl)) {
        out.add(dataUrl);
      }
    }
    return List.copyOf(out);
  }

  private String loadOne(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "";
    }
    String url = raw.trim();
    if (!url.startsWith(mediaPublicBase + "/") && !url.equals(mediaPublicBase)) {
      log.warn("skip image not on media public base");
      return "";
    }
    HttpURLConnection conn = null;
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        return "";
      }
      conn = (HttpURLConnection) uri.toURL().openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout((int) AiConstants.IMAGE_FETCH_TIMEOUT.toMillis());
      conn.setReadTimeout((int) AiConstants.IMAGE_FETCH_TIMEOUT.toMillis());
      conn.setRequestMethod("GET");
      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return "";
      }
      long stated = conn.getContentLengthLong();
      if (stated > AiConstants.CHAT_IMAGE_BYTES_MAX) {
        return "";
      }
      byte[] bytes;
      try (InputStream in = conn.getInputStream()) {
        bytes = in.readNBytes(AiConstants.CHAT_IMAGE_BYTES_MAX + 1);
      }
      if (bytes.length == 0 || bytes.length > AiConstants.CHAT_IMAGE_BYTES_MAX) {
        return "";
      }
      String mime = mimeOf(conn.getContentType(), url);
      if (!StringUtils.hasText(mime)) {
        return "";
      }
      return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    } catch (Exception ex) {
      log.warn("load chat image failed");
      return "";
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  private static String mimeOf(String contentType, String url) {
    String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if (type.startsWith("image/jpeg") || type.equals("image/jpg")) {
      return "image/jpeg";
    }
    if (type.equals("image/png") || type.equals("image/webp") || type.equals("image/gif")) {
      return type;
    }
    String lower = url.toLowerCase(Locale.ROOT);
    if (lower.contains(".png")) {
      return "image/png";
    }
    if (lower.contains(".webp")) {
      return "image/webp";
    }
    if (lower.contains(".gif")) {
      return "image/gif";
    }
    if (lower.contains(".jpg") || lower.contains(".jpeg")) {
      return "image/jpeg";
    }
    return "";
  }
}
