package com.onepage.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.user.entity.UserStat;
import org.apache.ibatis.annotations.Mapper;

/** 计数表访问。 */
@Mapper
public interface UserStatMapper extends BaseMapper<UserStat> {}
