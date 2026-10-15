package com.onepage.moderation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 治理服务（默认 8086）。公网只认网关 {@code X-User-Id}，不解析 Cookie。
 * 举报入库在本库；下架走 content、冻结账号走 auth，不要在本服务改帖/改账号表。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.moderation.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class ModerationApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(ModerationApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
