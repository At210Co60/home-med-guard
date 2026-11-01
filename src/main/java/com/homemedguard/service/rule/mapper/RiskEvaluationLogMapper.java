package com.homemedguard.service.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.rule.entity.RiskEvaluationLogEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskEvaluationLogMapper extends BaseMapper<RiskEvaluationLogEntity> {

    @Delete("DELETE FROM risk_evaluation_log WHERE member_id = #{memberId}")
    int eraseByMember(@Param("memberId") Long memberId);

    @Delete("DELETE FROM risk_evaluation_log WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
