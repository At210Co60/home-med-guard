package com.homemedguard.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.auth.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
}
