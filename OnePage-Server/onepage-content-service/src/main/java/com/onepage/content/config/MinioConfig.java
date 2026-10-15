package com.onepage.content.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketCorsArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.CORSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 创建 MinIO 客户端。启动时确保桶存在，并设「匿名可读」——头像 URL 才能不带签名打开。
 * 写文件走预签名 PUT，密钥不进浏览器。
 */
@Configuration
public class MinioConfig {

  private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

  @Bean
  public MinioClient minioClient(
      @Value("${onepage.minio.endpoint}") String endpoint,
      @Value("${onepage.minio.access-key}") String accessKey,
      @Value("${onepage.minio.secret-key}") String secretKey,
      @Value("${onepage.minio.bucket}") String bucket) throws Exception {
    MinioClient client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
    boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
    String policy = """
        {
          "Version": "2012-10-17",
          "Statement": [{
            "Effect": "Allow",
            "Principal": {"AWS": ["*"]},
            "Action": ["s3:GetObject"],
            "Resource": ["arn:aws:s3:::%s/*"]
          }]
        }
        """.formatted(bucket);
    client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
    applyBrowserCors(client, bucket);
    return client;
  }

  /**
   * 本机不少 MinIO 发行版不实现 PutBucketCors（会报 NotImplemented）。
   * 失败时不阻断启动；请在 MinIO 进程加 {@code MINIO_API_CORS_ALLOW_ORIGIN}，
   * 源要覆盖文档 5173、社区 5174、创作者 5175、运营 5176。
   */
  private void applyBrowserCors(MinioClient client, String bucket) {
    List<String> origins = List.of(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5174",
        "http://localhost:5175",
        "http://127.0.0.1:5175",
        "http://localhost:5176",
        "http://127.0.0.1:5176");
    CORSConfiguration cors = new CORSConfiguration(List.of(
        new CORSConfiguration.CORSRule(
            List.of("*"),
            List.of("GET", "PUT", "HEAD", "POST"),
            origins,
            List.of("ETag", "x-amz-request-id", "Content-Type"),
            "onepage-dev",
            3600)));
    try {
      client.setBucketCors(SetBucketCorsArgs.builder().bucket(bucket).config(cors).build());
    } catch (Exception ex) {
      log.warn(
          "MinIO 未接受桶 CORS（{}）。浏览器直传请给 MinIO 设置 MINIO_API_CORS_ALLOW_ORIGIN="
              + "http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://localhost:5175,http://127.0.0.1:5175,http://localhost:5176,http://127.0.0.1:5176",
          ex.getMessage());
    }
  }
}
