package com.onepage.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.auth.entity.AuthOauthBinding;
import org.apache.ibatis.annotations.Mapper;

/** OAuth 绑定表访问，方法来自 {@link BaseMapper}。 */
@Mapper
public interface AuthOauthBindingMapper extends BaseMapper<AuthOauthBinding> {}
