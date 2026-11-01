package com.homemedguard.service.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.stock.entity.DisposalRecordEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DisposalRecordMapper extends BaseMapper<DisposalRecordEntity> {

    @Delete("DELETE FROM disposal_record WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
