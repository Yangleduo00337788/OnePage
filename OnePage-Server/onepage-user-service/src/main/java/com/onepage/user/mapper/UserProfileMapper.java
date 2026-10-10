package com.onepage.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.user.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/** 资料表 CRUD，SQL 由 MyBatis-Plus 按实体生成。 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {}
