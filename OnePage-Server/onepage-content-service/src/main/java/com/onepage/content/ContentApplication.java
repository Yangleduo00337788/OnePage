package com.onepage.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 内容服务（默认 8083）。上传 + P1 笔记/Feed/赞藏评。
 * 注册到 Nacos 后网关用 {@code lb://onepage-content-service} 转发，不要把 8083 暴露给浏览器。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.content.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class ContentApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(ContentApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
