package com.homemedguard.service.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.entity.StorageLocationEntity;
import com.homemedguard.service.stock.mapper.StockBatchMapper;
import com.homemedguard.service.stock.mapper.StorageLocationMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 药箱存放位置（handoff §2「药箱分层」）：柜→层→格的树。
 * 只允许在同一家庭内成树；挂在某个格子里的批次还在时不许删这个格子，
 * 否则批次的存放位置会变成悬空引用，处置时人找不到药。
 */
@StockModule
public class StorageLocationService {

    /** 层深上限：再深就不是药箱而是仓库了，也顺带挡住脏数据造成的长循环。 */
    private static final int MAX_DEPTH = 6;

    public record SaveCommand(Long id,
                              Long familyId,
                              Long parentId,
                              String name,
                              Integer sortNo) {
    }

    private final StorageLocationMapper locations;
    private final StockBatchMapper batches;

    public StorageLocationService(StorageLocationMapper locations, StockBatchMapper batches) {
        this.locations = locations;
        this.batches = batches;
    }

    public List<StorageLocationEntity> list(Long familyId) {
        if (familyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "familyId 不能为空");
        }
        return locations.selectList(Wrappers.<StorageLocationEntity>lambdaQuery()
                .eq(StorageLocationEntity::getFamilyId, familyId)
                .orderByAsc(StorageLocationEntity::getParentId)
                .orderByAsc(StorageLocationEntity::getSortNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public StorageLocationEntity save(SaveCommand cmd) {
        if (cmd == null || cmd.familyId() == null || cmd.name() == null || cmd.name().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "位置名称与 familyId 不能为空");
        }
        StorageLocationEntity entity = cmd.id() == null ? new StorageLocationEntity()
                : requireOwned(cmd.id(), cmd.familyId());
        if (cmd.parentId() != null) {
            requireOwned(cmd.parentId(), cmd.familyId());
            assertNoCycle(entity.getId(), cmd.parentId());
        }
        entity.setFamilyId(cmd.familyId());
        entity.setParentId(cmd.parentId());
        entity.setName(cmd.name().trim());
        entity.setSortNo(cmd.sortNo() == null ? 0 : cmd.sortNo());
        if (entity.getId() == null) {
            locations.insert(entity);
        } else {
            locations.updateById(entity);
        }
        return entity;
    }

    /** 有子格子或有批次停在上面时拒绝删除，让调用方先迁移。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long familyId, Long locationId) {
        StorageLocationEntity location = requireOwned(locationId, familyId);
        long children = locations.selectCount(Wrappers.<StorageLocationEntity>lambdaQuery()
                .eq(StorageLocationEntity::getParentId, location.getId()));
        if (children > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该位置下还有 " + children + " 个子位置，先迁移再删");
        }
        long parked = batches.selectCount(Wrappers.<StockBatchEntity>lambdaQuery()
                .eq(StockBatchEntity::getLocationId, location.getId()));
        if (parked > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "还有 " + parked + " 个批次放在这里，先挪走再删，否则处置时找不到药");
        }
        locations.deleteById(location.getId());
    }

    private StorageLocationEntity requireOwned(Long locationId, Long familyId) {
        StorageLocationEntity location = locationId == null ? null : locations.selectById(locationId);
        if (location == null || !location.getFamilyId().equals(familyId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "位置不存在或不属于该家庭：" + locationId);
        }
        return location;
    }

    /** 从拟用的父节点往上走，遇到自己就是环；顺带校验层深。 */
    private void assertNoCycle(Long selfId, Long parentId) {
        Long cursor = parentId;
        int depth = 1;
        while (cursor != null) {
            if (cursor.equals(selfId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能把自己挪到自己的子树下，会成环");
            }
            StorageLocationEntity node = locations.selectById(cursor);
            if (node == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "父位置不存在：" + cursor);
            }
            cursor = node.getParentId();
            depth++;
        }
        if (depth >= MAX_DEPTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "药箱分层最多 " + MAX_DEPTH + " 层");
        }
    }
}
