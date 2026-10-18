package com.onepage.auth.service;

import com.onepage.common.api.BusinessException;
import com.onepage.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 第三方登录：先拼授权 URL，用户同意后用 {@code code} 换 access_token 再拉资料。
 * GitHub 用 JSON 换 token，Google 用 form-urlencoded——这是对方 API 规定，不是随便选的。
 */
@Service
public class OAuthService {
  private final RestClient restClient;
  private final String githubClientId;
  private final String githubClientSecret;
  private final String githubRedirect;
  private final String googleClientId;
  private final String googleClientSecret;
  private final String googleRedirect;

  public OAuthService(
      RestClient restClient,
      @Value("${onepage.oauth.github.client-id:}") String githubClientId,
      @Value("${onepage.oauth.github.client-secret:}") String githubClientSecret,
      @Value("${onepage.oauth.github.redirect-uri:}") String githubRedirect,
      @Value("${onepage.oauth.google.client-id:}") String googleClientId,
      @Value("${onepage.oauth.google.client-secret:}") String googleClientSecret,
      @Value("${onepage.oauth.google.redirect-uri:}") String googleRedirect) {
    this.restClient = restClient;
    this.githubClientId = githubClientId;
    this.githubClientSecret = githubClientSecret;
    this.githubRedirect = githubRedirect;
    this.googleClientId = googleClientId;
    this.googleClientSecret = googleClientSecret;
    this.googleRedirect = googleRedirect;
  }

  /** {@code switch} 表达式：每个 case 必须给出 URL 或抛错，漏写编译不过。 */
  public String authorizeUrl(String provider, String state) {
    return switch (provider) {
      case "github" -> {
        require(githubClientId, "GitHub");
        yield UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
            .queryParam("client_id", githubClientId)
            .queryParam("redirect_uri", githubRedirect)
            .queryParam("scope", "read:user user:email")
            .queryParam("state", state)
            .encode()
            .build()
            .toUriString();
      }
      case "google" -> {
        require(googleClientId, "Google");
        yield UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", googleClientId)
            .queryParam("redirect_uri", googleRedirect)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .encode()
            .build()
            .toUriString();
      }
      default -> throw BusinessException.validation("不支持的登录方式");
    };
  }

  public OAuthProfile exchange(String provider, String code) {
    return switch (provider) {
      case "github" -> github(code);
      case "google" -> google(code);
      default -> throw BusinessException.validation("不支持的登录方式");
    };
  }

  @SuppressWarnings("unchecked")
  private OAuthProfile github(String code) {
    require(githubClientId, "GitHub");
    Map<String, Object> token = restClient.post()
        .uri("https://github.com/login/oauth/access_token")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "client_id", githubClientId,
            "client_secret", githubClientSecret,
            "code", code,
            "redirect_uri", githubRedirect))
        .retrieve()
        .body(Map.class);
    String access = token == null ? null : String.valueOf(token.get("access_token"));
    if (!StringUtils.hasText(access) || "null".equals(access)) {
      throw BusinessException.unauthorized("GitHub 授权失败");
    }
    Map<String, Object> user = restClient.get()
        .uri("https://api.github.com/user")
        .header("Authorization", "Bearer " + access)
        .retrieve()
        .body(Map.class);
    if (user == null || user.get("id") == null) {
      throw BusinessException.unauthorized("GitHub 用户信息失败");
    }
    String name = firstText(user.get("name"), user.get("login"));
    String email = text(user.get("email"));
    if (!StringUtils.hasText(email)) {
      email = githubPrimaryEmail(access);
    }
    return new OAuthProfile(String.valueOf(user.get("id")), name, email);
  }

  @SuppressWarnings("unchecked")
  private OAuthProfile google(String code) {
    require(googleClientId, "Google");
    String body = "client_id=" + enc(googleClientId)
        + "&client_secret=" + enc(googleClientSecret)
        + "&code=" + enc(code)
        + "&grant_type=authorization_code"
        + "&redirect_uri=" + enc(googleRedirect);
    Map<String, Object> token = restClient.post()
        .uri("https://oauth2.googleapis.com/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(Map.class);
    String access = token == null ? null : String.valueOf(token.get("access_token"));
    if (!StringUtils.hasText(access) || "null".equals(access)) {
      throw BusinessException.unauthorized("Google 授权失败");
    }
    Map<String, Object> user = restClient.get()
        .uri("https://www.googleapis.com/oauth2/v3/userinfo")
        .header("Authorization", "Bearer " + access)
        .retrieve()
        .body(Map.class);
    if (user == null || user.get("sub") == null) {
      throw BusinessException.unauthorized("Google 用户信息失败");
    }
    String name = firstText(user.get("name"), "Google用户");
    return new OAuthProfile(String.valueOf(user.get("sub")), name, text(user.get("email")));
  }

  /** GitHub 公开资料经常没有 email，要用 user:email 再拉一次列表。拉失败不影响登录。 */
  private String githubPrimaryEmail(String access) {
    try {
      List<Map<String, Object>> rows = restClient.get()
          .uri("https://api.github.com/user/emails")
          .header("Authorization", "Bearer " + access)
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
      if (rows == null || rows.isEmpty()) {
        return null;
      }
      String verified = null;
      String any = null;
      for (Map<String, Object> row : rows) {
        String email = text(row.get("email"));
        if (!StringUtils.hasText(email)) {
          continue;
        }
        if (any == null) {
          any = email;
        }
        boolean primary = Boolean.TRUE.equals(row.get("primary"));
        boolean ok = Boolean.TRUE.equals(row.get("verified"));
        if (primary && ok) {
          return email;
        }
        if (ok && verified == null) {
          verified = email;
        }
      }
      return verified != null ? verified : any;
    } catch (RestClientException ignored) {
      return null;
    }
  }

  private String firstText(Object preferred, Object fallback) {
    String value = text(preferred);
    return value != null ? value : text(fallback);
  }

  private String text(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (!StringUtils.hasText(raw) || "null".equals(raw)) {
      return null;
    }
    return raw;
  }

  private void require(String clientId, String name) {
    if (!StringUtils.hasText(clientId)) {
      throw new BusinessException(ErrorCodes.CONFIG_MISSING, name + " 登录未配置 client-id", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public record OAuthProfile(String providerUserId, String nickname, String email) {}
}
