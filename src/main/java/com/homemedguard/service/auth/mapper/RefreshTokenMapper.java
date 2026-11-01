package com.homemedguard.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.auth.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {
}
