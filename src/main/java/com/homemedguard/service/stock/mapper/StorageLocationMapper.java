package com.homemedguard.service.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.stock.entity.StorageLocationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StorageLocationMapper extends BaseMapper<StorageLocationEntity> {

    @Delete("DELETE FROM storage_location WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
