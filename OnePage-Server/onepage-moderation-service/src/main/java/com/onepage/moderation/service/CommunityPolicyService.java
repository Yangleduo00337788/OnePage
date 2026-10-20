package com.onepage.moderation.service;

import com.onepage.common.api.BusinessException;
import com.onepage.moderation.dto.ModerationDtos;
import com.onepage.moderation.entity.CommunityPolicy;
import com.onepage.moderation.mapper.CommunityPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 单行社区规则。缺行时写入与代码一致的默认值。 */
@Service
public class CommunityPolicyService {
  static final int ROW_ID = 1;
  static final String DEFAULT_WORDS =
      "代孕\n办证\n发票\n枪支\n毒品\n约炮\n色情\n赌博\n加微信\n加vx\n加v信\n澳门赌场\n六合彩";
  private static final int WORD_MAX = 32;
  private static final int WORDS_MAX = 200;

  private final CommunityPolicyMapper mapper;

  public CommunityPolicyService(CommunityPolicyMapper mapper) {
    this.mapper = mapper;
  }

  public ModerationDtos.CommunityPolicyView view() {
    return toView(requireRow());
  }

  public ModerationDtos.CommunityPolicyView save(ModerationDtos.CommunityPolicyPatch body) {
    if (body == null) {
      throw BusinessException.validation("请求体不能为空");
    }
    CommunityPolicy row = requireRow();
    if (body.bannedWords() != null) {
      row.setBannedWords(joinWords(body.bannedWords()));
    }
    if (body.nicknameMax() != null) {
      row.setNicknameMax(clamp(body.nicknameMax(), 1, 64, "昵称长度"));
    }
    if (body.bioMax() != null) {
      row.setBioMax(clamp(body.bioMax(), 1, 500, "签名长度"));
    }
    if (body.imageMaxMb() != null) {
      row.setImageMaxMb(clamp(body.imageMaxMb(), 1, 50, "图片上限"));
    }
    if (body.videoMaxMb() != null) {
      row.setVideoMaxMb(clamp(body.videoMaxMb(), 1, 2048, "视频上限"));
    }
    row.setUpdatedAt(LocalDateTime.now());
    mapper.updateById(row);
    return toView(row);
  }

  private CommunityPolicy requireRow() {
    CommunityPolicy row = mapper.selectById(ROW_ID);
    if (row != null) {
      return row;
    }
    CommunityPolicy created = new CommunityPolicy();
    created.setId(ROW_ID);
    created.setBannedWords(DEFAULT_WORDS);
    created.setNicknameMax(24);
    created.setBioMax(160);
    created.setImageMaxMb(10);
    created.setVideoMaxMb(200);
    created.setUpdatedAt(LocalDateTime.now());
    mapper.insert(created);
    return created;
  }

  private static ModerationDtos.CommunityPolicyView toView(CommunityPolicy row) {
    return new ModerationDtos.CommunityPolicyView(
        splitWords(row.getBannedWords()),
        n(row.getNicknameMax(), 24),
        n(row.getBioMax(), 160),
        n(row.getImageMaxMb(), 10),
        n(row.getVideoMaxMb(), 200));
  }

  static List<String> splitWords(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String line : raw.split("\\R")) {
      String word = line == null ? "" : line.trim();
      if (StringUtils.hasText(word)) {
        out.add(word);
      }
    }
    return new ArrayList<>(out);
  }

  private static String joinWords(List<String> words) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (words != null) {
      for (String raw : words) {
        if (!StringUtils.hasText(raw)) {
          continue;
        }
        String word = raw.trim();
        if (word.length() > WORD_MAX) {
          throw BusinessException.validation("违禁词不能超过 32 字");
        }
        out.add(word);
        if (out.size() > WORDS_MAX) {
          throw BusinessException.validation("违禁词不能超过 200 条");
        }
      }
    }
    return String.join("\n", out);
  }

  private static int clamp(int value, int min, int max, String label) {
    if (value < min || value > max) {
      throw BusinessException.validation(label + "须在 " + min + "～" + max + " 之间");
    }
    return value;
  }

  private static int n(Integer value, int fallback) {
    return value == null || value <= 0 ? fallback : value;
  }
}
