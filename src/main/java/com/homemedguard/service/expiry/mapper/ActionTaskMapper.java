package com.homemedguard.service.expiry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.expiry.entity.ActionTaskEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActionTaskMapper extends BaseMapper<ActionTaskEntity> {

    /** 账号注销时跟着家庭一起清：任务里带着药名与效期，属于个人健康相关数据。 */
    @Delete("DELETE FROM action_task WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
