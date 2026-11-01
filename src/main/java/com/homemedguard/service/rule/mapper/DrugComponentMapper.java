package com.homemedguard.service.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.rule.entity.DrugComponentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DrugComponentMapper extends BaseMapper<DrugComponentEntity> {

    /** 注销走物理删除：逻辑删除只是把行藏起来，数据还在，不能算"已删除"。 */
    @Delete("DELETE FROM drug_component WHERE drug_dict_id = #{drugDictId}")
    int eraseByDrugDict(@Param("drugDictId") Long drugDictId);
}
