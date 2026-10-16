package com.onepage.live;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 直播服务（默认 8089）。公网只认网关 {@code X-User-Id}，不解析 Cookie。
 * 推流/HLS 地址来自环境变量，本服务不内嵌 SRS。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.live.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class LiveApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(LiveApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
    System.setProperty("java.net.preferIPv6Addresses", "true");
  }
}
