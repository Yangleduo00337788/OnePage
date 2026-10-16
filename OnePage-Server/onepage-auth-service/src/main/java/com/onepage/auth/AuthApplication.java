package com.onepage.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
/**
 * 认证服务入口（默认 8081）。只管「你是谁」：短信、账密、OAuth；不管资料文案。
 * <p>
 * Session 走 Boot 自动配置（yml {@code spring.session.store-type=redis} + {@code timeout}），
 * 不要加 {@code @EnableRedisHttpSession}：注解默认 1800 秒，会盖掉 {@code SESSION_TIMEOUT}。
 * {@code @EnableFeignClients}：扫描 {@code client} 包，按 Nacos 服务名调 user-service。
 * {@code @MapperScan}：MyBatis 接口不用手写 XML 就能注入。
 * {@code scanBasePackages = "com.onepage"}：才能扫到 common 里的 {@code GlobalExceptionHandler}。
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.onepage.auth.mapper")
@SpringBootApplication(scanBasePackages = "com.onepage")
public class AuthApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(AuthApplication.class, args);
  }

  /** 与网关相同：本机系统代理会掐掉 Nacos HTTP 登录。 */
  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
