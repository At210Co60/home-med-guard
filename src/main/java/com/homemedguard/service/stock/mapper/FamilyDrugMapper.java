package com.homemedguard.service.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FamilyDrugMapper extends BaseMapper<FamilyDrugEntity> {

    @Delete("DELETE FROM family_drug WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
