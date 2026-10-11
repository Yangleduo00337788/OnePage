package com.onepage.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onepage.content.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {}
