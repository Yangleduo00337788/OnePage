package com.onepage.live.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;

/** 查推流是否还在。优先 MediaMTX API，否则拉 HLS 列表。失败返回 null，本轮不关场。 */
@Component
public class LiveIngestProbe {
  private static final Logger log = LoggerFactory.getLogger(LiveIngestProbe.class);
  private final RestClient http;
  private final ObjectMapper objectMapper;
  private final String hlsBase;
  private final String mtxApiBase;
  private final String mtxPathPrefix;

  public LiveIngestProbe(
      ObjectMapper objectMapper,
      @Value("${onepage.live-hls-base}") String hlsBase,
      @Value("${onepage.live-mtx-api-base}") String mtxApiBase) {
    this.objectMapper = objectMapper;
    this.hlsBase = trimSlash(hlsBase);
    this.mtxApiBase = trimSlash(mtxApiBase);
    this.mtxPathPrefix = pathPrefix(this.hlsBase);
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(800));
    factory.setReadTimeout(Duration.ofMillis(1_200));
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  /** true 在推；false 已停；null 探不到（MediaMTX 重启等），不要据此关播。 */
  public Boolean publishing(String streamKey) {
    if (!StringUtils.hasText(streamKey)) {
      return null;
    }
    Boolean fromApi = fromMtx(streamKey);
    if (fromApi != null) {
      return fromApi;
    }
    return fromHls(streamKey);
  }

  private Boolean fromMtx(String streamKey) {
    if (!StringUtils.hasText(mtxApiBase)) {
      return null;
    }
    String name = mtxPathPrefix.isEmpty() ? streamKey : mtxPathPrefix + "/" + streamKey;
    String url = mtxApiBase + "/v3/paths/get/" + name.replace("/", "%2F");
    try {
      String body = http.get().uri(url).retrieve().body(String.class);
      if (!StringUtils.hasText(body)) {
        return null;
      }
      JsonNode node = objectMapper.readTree(body);
      JsonNode ready = node.get("ready");
      if (ready == null || !ready.isBoolean()) {
        return null;
      }
      return ready.booleanValue();
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return false;
      }
      log.debug("mtx path probe http {}", ex.getStatusCode().value());
      return null;
    } catch (JsonProcessingException | RuntimeException ex) {
      log.debug("mtx path probe failed");
      return null;
    }
  }

  private Boolean fromHls(String streamKey) {
    if (!StringUtils.hasText(hlsBase)) {
      return null;
    }
    String url = hlsBase + "/" + streamKey + "/index.m3u8";
    try {
      String body = http.get().uri(URI.create(url)).retrieve().body(String.class);
      if (body != null && body.contains("#EXTM3U")) {
        return true;
      }
      return false;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return false;
      }
      return null;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static String pathPrefix(String hlsBase) {
    if (!StringUtils.hasText(hlsBase)) {
      return "";
    }
    try {
      String path = URI.create(hlsBase).getPath();
      if (!StringUtils.hasText(path) || "/".equals(path)) {
        return "";
      }
      while (path.startsWith("/")) {
        path = path.substring(1);
      }
      while (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      return path;
    } catch (RuntimeException ex) {
      return "live";
    }
  }

  private static String trimSlash(String raw) {
    if (raw == null) {
      return "";
    }
    String text = raw.trim();
    return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
  }
}
