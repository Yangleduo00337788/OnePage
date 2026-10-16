package com.onepage.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 搜索服务（默认 8087）。公网 {@code GET /api/v1/search} 只认网关，不解析 Cookie。
 * 索引在 Elasticsearch；MQ 未消费时由 content/user Feign 投递，启动后再回填。
 */
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.onepage", exclude = DataSourceAutoConfiguration.class)
public class SearchApplication {
  public static void main(String[] args) {
    bypassLocalHttpProxy();
    SpringApplication.run(SearchApplication.class, args);
  }

  private static void bypassLocalHttpProxy() {
    String hosts = "localhost|127.0.0.1|::1|[::1]";
    System.setProperty("http.nonProxyHosts", hosts);
    System.setProperty("https.nonProxyHosts", hosts);
    System.setProperty("java.net.useSystemProxies", "false");
    System.setProperty("java.net.preferIPv6Addresses", "true");
  }
}
