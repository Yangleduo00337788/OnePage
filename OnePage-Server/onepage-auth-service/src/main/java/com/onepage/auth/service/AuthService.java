package com.onepage.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onepage.auth.client.UserClient;
import com.onepage.auth.entity.AuthAccount;
import com.onepage.auth.entity.AuthOauthBinding;
import com.onepage.auth.mapper.AuthAccountMapper;
import com.onepage.auth.mapper.AuthOauthBindingMapper;
import com.onepage.common.api.BusinessException;
import com.onepage.common.dto.UserSummary;
import com.onepage.common.id.IdGenerator;
import com.onepage.common.ops.OpsStaffAllowlist;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 登录注册编排：写 auth 库、调 user-service 建档、再把 userId 放进 HttpSession（Redis）。
 * 短信校验见 {@link SmsService}，当前环境由 {@code SMS_MOCK} 决定是否走真实通道。
 */
@Service
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final int NICKNAME_MAX = 64;
  private static final int EMAIL_MAX = 128;
  private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
  public static final String OAUTH_OK = "ok";
  public static final String OAUTH_EMAIL_TAKEN = "email_taken";
  public static final String OAUTH_BIND_OK = "bind_ok";
  public static final String OAUTH_ALREADY_BOUND = "already_bound";
  public static final String OAUTH_NEED_LOGIN = "need_login";
  private final AuthAccountMapper accountMapper;
  private final AuthOauthBindingMapper oauthMapper;
  private final SmsService smsService;
  private final PasswordEncoder passwordEncoder;
  private final UserClient userClient;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redis;
  private final String internalToken;
  private final Set<Long> opsStaffIds;

  public AuthService(
      AuthAccountMapper accountMapper,
      AuthOauthBindingMapper oauthMapper,
      SmsService smsService,
      PasswordEncoder passwordEncoder,
      UserClient userClient,
      ObjectMapper objectMapper,
      StringRedisTemplate redis,
      @Value("${onepage.internal-token}") String internalToken,
      @Value("${onepage.ops-staff-user-ids:}") String opsStaffUserIds) {
    this.accountMapper = accountMapper;
    this.oauthMapper = oauthMapper;
    this.smsService = smsService;
    this.passwordEncoder = passwordEncoder;
    this.userClient = userClient;
    this.objectMapper = objectMapper;
    this.redis = redis;
    this.internalToken = internalToken;
    this.opsStaffIds = OpsStaffAllowlist.parse(opsStaffUserIds);
  }

  /** scene 必须和校验时同一个词，否则 Redis key 对不上。 */
  public int sendSms(String phone, String scene) {
    if (!"login".equals(scene) && !"register".equals(scene) && !"reset".equals(scene)) {
      throw BusinessException.validation("scene 只能是 login、register 或 reset");
    }
    return smsService.send(phone, scene);
  }

  /**
   * {@code @Transactional}：本方法里多步写库失败则全部回滚。
   * 短信登录允许「没注册就当场建号」，减少一步注册。
   */
  @Transactional
  public UserSummary smsLogin(String phone, String code, HttpServletRequest request, boolean opsOnly) {
    smsService.verify(phone, "login", code);
    AuthAccount account = accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getPhone, phone));
    if (opsOnly) {
      if (account == null) {
        throw BusinessException.forbidden("不是运营员工");
      }
      assertActive(account);
      OpsStaffAllowlist.requireStaff(opsStaffIds, account.getId());
      return loginSession(account.getId(), request);
    }
    if (account == null) {
      account = newAccount(phone, null, null);
      accountMapper.insert(account);
      ensureProfile(account.getId(), defaultNickname(phone));
    }
    assertActive(account);
    return loginSession(account.getId(), request);
  }

  @Transactional
  public UserSummary register(String phone, String email, String password, String nickname, HttpServletRequest request) {
    if (!StringUtils.hasText(phone) && !StringUtils.hasText(email)) {
      throw BusinessException.validation("手机号或邮箱至少填一项");
    }
    assertPassword(password);
    if (StringUtils.hasText(phone)
        && accountMapper.selectOne(Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getPhone, phone)) != null) {
      throw BusinessException.conflict("手机号已注册");
    }
    if (StringUtils.hasText(email)
        && accountMapper.selectOne(Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getEmail, email)) != null) {
      throw BusinessException.conflict("邮箱已注册");
    }
    AuthAccount account = newAccount(phone, email, passwordEncoder.encode(password));
    accountMapper.insert(account);
    String name = StringUtils.hasText(nickname) ? nickname : defaultNickname(phone != null ? phone : email);
    ensureProfile(account.getId(), name);
    return loginSession(account.getId(), request);
  }

  /** {@code passwordEncoder.matches}：明文对 BCrypt 哈希，不能用 {@code equals}。 */
  public UserSummary passwordLogin(String account, String password, HttpServletRequest request, boolean opsOnly) {
    AuthAccount row = findByAccount(account);
    if (row == null || !StringUtils.hasText(row.getPasswordHash())
        || !passwordEncoder.matches(password, row.getPasswordHash())) {
      throw BusinessException.unauthorized("账号或密码不正确");
    }
    assertActive(row);
    if (opsOnly) {
      OpsStaffAllowlist.requireStaff(opsStaffIds, row.getId());
    }
    return loginSession(row.getId(), request);
  }

  public String newOAuthState(boolean bind, Long userId) {
    if (bind && userId == null) {
      return null;
    }
    String token = UUID.randomUUID().toString().replace("-", "");
    String payload = bind ? "bind:" + userId : "login";
    redis.opsForValue().set("oauth:state:" + token, payload, OAUTH_STATE_TTL);
    return token;
  }

  public OAuthIntent consumeOAuthState(String state) {
    if (!StringUtils.hasText(state)) {
      return OAuthIntent.login();
    }
    String payload = redis.opsForValue().getAndDelete("oauth:state:" + state);
    if (!StringUtils.hasText(payload) || payload.equals("login")) {
      return OAuthIntent.login();
    }
    if (payload.startsWith("bind:")) {
      try {
        return new OAuthIntent(true, Long.parseLong(payload.substring(5)));
      } catch (NumberFormatException ignored) {
        return OAuthIntent.login();
      }
    }
    return OAuthIntent.login();
  }

  /**
   * 按「平台 + 平台用户 id」找绑定。没有绑定时：邮箱已被占用则不开新号（避免和手机注册号裂开）；
   * 邮箱空或未被占用才建号。
   */
  @Transactional
  public String oauthLogin(
      String provider,
      String providerUserId,
      String nickname,
      String email,
      HttpServletRequest request) {
    AuthOauthBinding binding = findBinding(provider, providerUserId);
    if (binding == null) {
      AuthAccount emailOwner = findByEmail(email);
      if (emailOwner != null) {
        log.warn("OAuth 登录邮箱已注册，拒绝开新号");
        return OAUTH_EMAIL_TAKEN;
      }
      AuthAccount account = newAccount(null, unusedEmail(email, null), null);
      accountMapper.insert(account);
      insertBinding(account.getId(), provider, providerUserId);
      String name = StringUtils.hasText(nickname) ? nickname : provider + "用户";
      ensureProfile(account.getId(), clip(name, NICKNAME_MAX));
      assertActive(account);
      loginSession(account.getId(), request);
      return OAUTH_OK;
    }
    AuthAccount account = accountMapper.selectById(binding.getUserId());
    assertActive(account);
    fillEmailIfEmpty(account, email);
    loginSession(binding.getUserId(), request);
    return OAUTH_OK;
  }

  /** 已登录绑定。不要求邮箱相同。对方账号已绑别人则失败。 */
  @Transactional
  public String oauthBind(long userId, String provider, String providerUserId, String email) {
    AuthAccount account = accountMapper.selectById(userId);
    assertActive(account);
    AuthOauthBinding byProvider = findBinding(provider, providerUserId);
    if (byProvider != null) {
      return Objects.equals(userId, byProvider.getUserId()) ? OAUTH_BIND_OK : OAUTH_ALREADY_BOUND;
    }
    AuthOauthBinding mine = oauthMapper.selectOne(
        Wrappers.<AuthOauthBinding>lambdaQuery()
            .eq(AuthOauthBinding::getUserId, userId)
            .eq(AuthOauthBinding::getProvider, provider));
    if (mine != null) {
      return Objects.equals(mine.getProviderUserId(), providerUserId)
          ? OAUTH_BIND_OK
          : OAUTH_ALREADY_BOUND;
    }
    insertBinding(userId, provider, providerUserId);
    fillEmailIfEmpty(account, email);
    return OAUTH_BIND_OK;
  }

  private AuthOauthBinding findBinding(String provider, String providerUserId) {
    return oauthMapper.selectOne(
        Wrappers.<AuthOauthBinding>lambdaQuery()
            .eq(AuthOauthBinding::getProvider, provider)
            .eq(AuthOauthBinding::getProviderUserId, providerUserId));
  }

  private boolean hasBinding(long userId, String provider) {
    return oauthMapper.selectOne(
        Wrappers.<AuthOauthBinding>lambdaQuery()
            .eq(AuthOauthBinding::getUserId, userId)
            .eq(AuthOauthBinding::getProvider, provider)) != null;
  }

  private void insertBinding(long userId, String provider, String providerUserId) {
    AuthOauthBinding created = new AuthOauthBinding();
    created.setId(IdGenerator.nextId());
    created.setUserId(userId);
    created.setProvider(provider);
    created.setProviderUserId(providerUserId);
    created.setCreatedAt(LocalDateTime.now());
    oauthMapper.insert(created);
  }

  private AuthAccount findByEmail(String email) {
    String value = clip(email, EMAIL_MAX);
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getEmail, value));
  }

  private void fillEmailIfEmpty(AuthAccount account, String email) {
    if (account == null || StringUtils.hasText(account.getEmail())) {
      return;
    }
    String fill = unusedEmail(email, account.getId());
    if (!StringUtils.hasText(fill)) {
      return;
    }
    account.setEmail(fill);
    account.setUpdatedAt(LocalDateTime.now());
    accountMapper.updateById(account);
  }

  /** 邮箱唯一；已被别人占用就空着，不挡登录。 */
  private String unusedEmail(String email, Long selfUserId) {
    String value = clip(email, EMAIL_MAX);
    if (!StringUtils.hasText(value)) {
      return null;
    }
    AuthAccount occupied = accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getEmail, value));
    if (occupied == null) {
      return value;
    }
    if (selfUserId != null && selfUserId.equals(occupied.getId())) {
      return value;
    }
    log.warn("OAuth 邮箱已被占用，跳过写入");
    return null;
  }

  private String clip(String value, int max) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  public AccountView account(Long userId) {
    AuthAccount row = accountMapper.selectById(userId);
    if (row == null) {
      throw BusinessException.unauthorized("未登录");
    }
    return new AccountView(
        row.getPhone() == null ? "" : row.getPhone(),
        row.getEmail() == null ? "" : row.getEmail(),
        StringUtils.hasText(row.getPasswordHash()),
        hasBinding(userId, "github"),
        hasBinding(userId, "google"));
  }

  /** 已有密码必须带旧密码；OAuth 用户第一次设密可以不带旧密码。 */
  public AccountView setPassword(Long userId, String oldPassword, String password) {
    assertPassword(password);
    AuthAccount row = accountMapper.selectById(userId);
    if (row == null) {
      throw BusinessException.unauthorized("未登录");
    }
    if (StringUtils.hasText(row.getPasswordHash())) {
      if (!StringUtils.hasText(oldPassword) || !passwordEncoder.matches(oldPassword, row.getPasswordHash())) {
        throw BusinessException.unauthorized("原密码不正确");
      }
    }
    row.setPasswordHash(passwordEncoder.encode(password));
    row.setUpdatedAt(LocalDateTime.now());
    accountMapper.updateById(row);
    return account(userId);
  }

  @Transactional
  public UserSummary resetPassword(String phone, String code, String password, HttpServletRequest request) {
    smsService.verify(phone, "reset", code);
    assertPassword(password);
    AuthAccount row = accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getPhone, phone));
    if (row == null) {
      throw BusinessException.notFound("该手机号尚未注册，请先用验证码登录");
    }
    assertActive(row);
    row.setPasswordHash(passwordEncoder.encode(password));
    row.setUpdatedAt(LocalDateTime.now());
    accountMapper.updateById(row);
    return loginSession(row.getId(), request);
  }

  public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  /**
   * {@code getSession(true)}：没有就创建。属性名必须是 {@code userId}，网关 Filter 按这个键读。
   * 资料从 user-service 取，auth 库没有昵称头像。
   */
  private UserSummary loginSession(Long userId, HttpServletRequest request) {
    HttpSession session = request.getSession(true);
    session.setAttribute("userId", String.valueOf(userId));
    // audience=ops 时 CookieSerializer 只读写运营 Cookie，不会覆盖社区 Session。
    return userClient.summary(internalToken, userId);
  }

  /** 调 user-service 内部接口建档；带 internal-token，防止别人直接打 8082。 */
  private void ensureProfile(Long userId, String nickname) {
    try {
      userClient.createProfile(internalToken, new UserClient.CreateProfileRequest(userId, nickname));
    } catch (FeignException ex) {
      if (ex.status() == 400) {
        throw BusinessException.validation(feignMessage(ex, "含有不允许的内容"));
      }
      throw ex;
    }
  }

  private String feignMessage(FeignException ex, String fallback) {
    try {
      JsonNode node = objectMapper.readTree(ex.contentUTF8());
      if (node != null && node.hasNonNull("message")) {
        String text = node.get("message").asText();
        if (StringUtils.hasText(text)) {
          return text;
        }
      }
    } catch (Exception ignored) {
      /* 用 fallback */
    }
    return fallback;
  }

  /** 登录框同一输入：先当手机号查，没有再当邮箱。 */
  private AuthAccount findByAccount(String account) {
    AuthAccount byPhone = accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getPhone, account));
    if (byPhone != null) {
      return byPhone;
    }
    return accountMapper.selectOne(
        Wrappers.<AuthAccount>lambdaQuery().eq(AuthAccount::getEmail, account));
  }

  private AuthAccount newAccount(String phone, String email, String passwordHash) {
    LocalDateTime now = LocalDateTime.now();
    AuthAccount account = new AuthAccount();
    account.setId(IdGenerator.nextId());
    account.setPhone(blankToNull(phone));
    account.setEmail(blankToNull(email));
    account.setPasswordHash(passwordHash);
    account.setStatus(0);
    account.setCreatedAt(now);
    account.setUpdatedAt(now);
    return account;
  }

  private void assertPassword(String password) {
    if (!StringUtils.hasText(password) || password.length() < 8 || password.length() > 32) {
      throw BusinessException.validation("密码需 8～32 位，并同时包含字母和数字");
    }
    boolean letter = false;
    boolean digit = false;
    for (int i = 0; i < password.length(); i++) {
      char c = password.charAt(i);
      if (Character.isLetter(c)) {
        letter = true;
      } else if (Character.isDigit(c)) {
        digit = true;
      }
    }
    if (!letter || !digit) {
      throw BusinessException.validation("密码需 8～32 位，并同时包含字母和数字");
    }
  }

  /** 运营封禁：status=1 后不能再登录。账号不存在 404。 */
  public void freeze(long userId) {
    AuthAccount account = accountMapper.selectById(userId);
    if (account == null) {
      throw BusinessException.notFound("账号不存在");
    }
    if (account.getStatus() != null && account.getStatus() != 0) {
      return;
    }
    account.setStatus(1);
    account.setUpdatedAt(LocalDateTime.now());
    accountMapper.updateById(account);
  }

  /** 运营解冻：status 回到 0。 */
  public void unfreeze(long userId) {
    AuthAccount account = accountMapper.selectById(userId);
    if (account == null) {
      throw BusinessException.notFound("账号不存在");
    }
    if (account.getStatus() != null && account.getStatus() == 0) {
      return;
    }
    account.setStatus(0);
    account.setUpdatedAt(LocalDateTime.now());
    accountMapper.updateById(account);
  }

  public FreezeView freezeStatus(long userId) {
    AuthAccount account = accountMapper.selectById(userId);
    if (account == null) {
      throw BusinessException.notFound("账号不存在");
    }
    boolean frozen = account.getStatus() != null && account.getStatus() != 0;
    return new FreezeView(String.valueOf(userId), frozen);
  }

  private void assertActive(AuthAccount account) {
    if (account == null || account.getStatus() == null || account.getStatus() != 0) {
      throw BusinessException.unauthorized("账号不可用");
    }
  }

  private String defaultNickname(String seed) {
    if (!StringUtils.hasText(seed)) {
      return "一页用户";
    }
    String tail = seed.length() > 4 ? seed.substring(seed.length() - 4) : seed;
    return "一页" + tail;
  }

  private String blankToNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }

  public record AccountView(
      String phone, String email, boolean hasPassword, boolean githubBound, boolean googleBound) {}

  public record OAuthIntent(boolean bind, Long userId) {
    static OAuthIntent login() {
      return new OAuthIntent(false, null);
    }
  }

  public record FreezeView(String userId, boolean frozen) {}
}
