package com.homemedguard.service.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockBatchMapper extends BaseMapper<StockBatchEntity> {

    /**
     * 带条件的原子扣减：数量不足时返回 0 行，调用方据此判定超领。
     * 用 SQL 里的 quantity >= #{qty} 兜并发，不要改成"先读再写"。
     */
    @Update("UPDATE stock_batch SET quantity = quantity - #{qty}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND quantity >= #{qty} AND deleted = 0")
    int decreaseQuantity(@Param("id") Long id, @Param("qty") int qty);

    /** 账号注销用的物理清除：逻辑删除对"删除我的全部数据"不成立（handoff §10）。 */
    @Delete("DELETE FROM stock_batch WHERE family_id = #{familyId}")
    int eraseByFamily(@Param("familyId") Long familyId);
}
