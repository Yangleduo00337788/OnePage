package com.onepage.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户资料服务（默认 8082）。公网接口只认网关注入的 {@code X-User-Id}。
 * 内部 {@code /internal/**} 用 {@code X-Internal-Token}，供 auth 注册时建档。
 * 没有 Redis Session：不解析 Cookie，避免和网关两套 Session 语义打架。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.user.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class UserApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(UserApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
