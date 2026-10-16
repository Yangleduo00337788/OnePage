package com.onepage.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ES REST 客户端。本机 WSL Docker 只通 IPv6 {@code ::1}，Java 解析 {@code localhost} 会走到
 * {@code 127.0.0.1} 然后 Connection reset。
 */
@Configuration
public class ElasticsearchConfig {
  private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

  @Bean(destroyMethod = "close")
  public RestClient elasticsearchRestClient(
      @Value("${onepage.es-uris}") String uris,
      @Value("${onepage.es-username:}") String username,
      @Value("${onepage.es-password:}") String password) {
    URI uri = URI.create(uris.trim());
    String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
    String host = loopbackToIpv6(uri.getHost());
    int port = uri.getPort() > 0 ? uri.getPort() : 9200;
    log.info("ES REST {}://{}:{}", scheme, host, port);
    HttpHost httpHost = new HttpHost(host, port, scheme);
    var builder = RestClient.builder(httpHost);
    builder.setRequestConfigCallback(req -> req.setConnectTimeout(5_000).setSocketTimeout(30_000));
    List<Header> headers = new ArrayList<>();
    headers.add(new BasicHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8"));
    if (StringUtils.hasText(username)) {
      String token = Base64.getEncoder()
          .encodeToString((username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
      headers.add(new BasicHeader("Authorization", "Basic " + token));
      BasicCredentialsProvider creds = new BasicCredentialsProvider();
      creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
      builder.setHttpClientConfigCallback(http -> http
          .setDefaultCredentialsProvider(creds)
          .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE)));
    } else {
      builder.setHttpClientConfigCallback(http ->
          http.setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE)));
    }
    builder.setDefaultHeaders(headers.toArray(Header[]::new));
    return builder.build();
  }

  @Bean
  public ElasticsearchClient elasticsearchClient(RestClient restClient) {
    return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
  }

  /** 本机环回一律走 IPv6，避开 Windows 上 127.0.0.1:9200 不通。 */
  static String loopbackToIpv6(String host) {
    if (host == null || host.isBlank()) {
      return "::1";
    }
    String h = host;
    if (h.startsWith("[") && h.endsWith("]")) {
      h = h.substring(1, h.length() - 1);
    }
    if ("localhost".equalsIgnoreCase(h) || "127.0.0.1".equals(h) || "::1".equals(h)) {
      return "::1";
    }
    return h;
  }
}
