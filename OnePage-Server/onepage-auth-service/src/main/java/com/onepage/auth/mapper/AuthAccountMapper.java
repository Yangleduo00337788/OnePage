package com.onepage.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.auth.entity.AuthAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus {@code BaseMapper} 已提供 insert/selectById/lambdaQuery，这里不用写 XML。
 * {@code @Mapper} 让 Spring 生成实现类并注入。
 */
@Mapper
public interface AuthAccountMapper extends BaseMapper<AuthAccount> {}
