package com.onepage.im;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 私信（默认 8085）。公网只认网关 {@code X-User-Id}，不解析 Cookie。
 * HTTP 会话/文本/图片/笔记卡片 + {@code WS /ws/im}（网关注入 X-User-Id）。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.im.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class ImApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(ImApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
