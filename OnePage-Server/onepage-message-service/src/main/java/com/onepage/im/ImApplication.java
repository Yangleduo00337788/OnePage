package com.onepage.im;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 私信（默认 8085）。公网只认网关 {@code X-User-Id}，不解析 Cookie。
 * 本期只做 HTTP 会话与文本消息，WebSocket 尚未接。
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
