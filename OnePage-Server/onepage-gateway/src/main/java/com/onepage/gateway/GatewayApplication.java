package com.onepage.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

/**
 * API 网关进程入口（默认端口 8080）。浏览器只应访问这一端口。
 * <p>
 * {@code @SpringBootApplication}：扫描本包、自动配置、当作启动类。<br>
 * {@code @EnableDiscoveryClient}：向 Nacos 注册自己，并从 Nacos 拿 auth/user/content 实例，
 * YAML 里才能写 {@code lb://onepage-auth-service}（lb = load balancer，按服务名找机器）。<br>
 * {@code @EnableRedisWebSession}：网关是 WebFlux（响应式），Session 走 Redis。
 * Cookie 名必须和 auth 的 Servlet Session 一致（{@code ONEPAGE_SESSION}），否则登录后下一请求仍 401。
 */
@EnableDiscoveryClient
@EnableRedisWebSession
@SpringBootApplication
public class GatewayApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(GatewayApplication.class, args);
  }

  /**
   * 启动最早执行：关掉 JVM 对 localhost 的系统 HTTP 代理。
   * Windows 开了系统代理时，Nacos SDK 访问 {@code 127.0.0.1:8848} 会被代理拦掉，表现为登录 401。
   * {@code System.setProperty} 是 JVM 全局属性，必须在连 Nacos 之前设。
   */
  static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
