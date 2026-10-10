package com.onepage.gateway;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 开发期诊断：Spring 创建完 Nacos 相关 Bean 后，打印实际绑定的地址/账号，并探测登录接口。
 * {@link BeanPostProcessor}：每个 Bean 初始化后回调一次，不是业务流量路径。
 * <p>
 * SCA 日志里 {@code serverAddr=null} 经常是 toString 写反了，要以这里打印的为准。
 * {@code NacosServiceManager} 关掉再由框架重建，避免本机热重启留下半死的 NamingService。
 */
@Component
public class NacosBindCheck implements BeanPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(NacosBindCheck.class);

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof NacosDiscoveryProperties props) {
      log.info(
          "Nacos 实际绑定: serverAddr={} namespace={} username空={} proxyHost={}",
          props.getServerAddr(),
          props.getNamespace(),
          !StringUtils.hasText(props.getUsername()),
          System.getProperty("http.proxyHost"));
      smokeLogin(props);
    }
    if (bean instanceof NacosServiceManager manager) {
      try {
        manager.nacosServiceShutDown();
      } catch (Exception ex) {
        log.warn("清理 NamingService: {}", ex.toString());
      }
    }
    return bean;
  }

  /** 依次试 IPv6 / localhost / 旁路 8849 / IPv4，看哪条能拿到 accessToken。 */
  private void smokeLogin(NacosDiscoveryProperties props) {
    if (!StringUtils.hasText(props.getUsername()) || !StringUtils.hasText(props.getPassword())) {
      log.error("Nacos username/password 未绑定");
      return;
    }
    String body =
        "username="
            + urlEncode(props.getUsername())
            + "&password="
            + urlEncode(props.getPassword());
    String[] urls = {
      "http://[::1]:8848/nacos/v3/auth/user/login",
      "http://localhost:8848/nacos/v3/auth/user/login",
      "http://127.0.0.1:8849/v3/auth/user/login",
      "http://127.0.0.1:8848/nacos/v3/auth/user/login"
    };
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .proxy(noProxy())
            .build();
    for (String url : urls) {
      try {
        HttpRequest req =
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        boolean token = resp.body() != null && resp.body().contains("accessToken");
        log.info("Nacos 登录探测 {} HTTP {} accessToken={}", url, resp.statusCode(), token);
        if (token) {
          return;
        }
      } catch (Exception ex) {
        log.warn("Nacos 登录探测失败 {}: {}", url, ex.getClass().getSimpleName() + ": " + ex.getMessage());
      }
    }
  }

  private static ProxySelector noProxy() {
    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY);
      }

      @Override
      public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {}
    };
  }

  private static String urlEncode(String raw) {
    return java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8);
  }
}
