package com.onepage.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知收件箱（默认 8084）。公网只认网关 {@code X-User-Id}，不解析 Cookie。
 * 内部 {@code /internal/notifications} 用 {@code X-Internal-Token}，由 content/user 在事务提交后投递。
 * RocketMQ 尚未消费，本期不用 MQ。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.notification.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class NotificationApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(NotificationApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
