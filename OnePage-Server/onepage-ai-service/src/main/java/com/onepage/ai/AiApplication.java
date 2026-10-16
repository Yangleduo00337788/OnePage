package com.onepage.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务（默认 8088）。公网只认网关 {@code X-User-Id}。
 * 润色与耶耶问答走 HTTP Chat Completions，检索走 onepage-search-service。
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.onepage", exclude = DataSourceAutoConfiguration.class)
public class AiApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(AiApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
  }
}
