package com.homemedguard.service.family;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.contract.AccountErasureRequestedEvent;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.auth.entity.SysUserEntity;
import com.homemedguard.service.auth.mapper.SysUserMapper;
import com.homemedguard.service.drug.DrugAttachmentService;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import com.homemedguard.service.drug.mapper.DrugDictMapper;
import com.homemedguard.service.family.entity.FamilyMemberEntity;
import com.homemedguard.service.family.mapper.FamilyMemberMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "删除我的全部数据"（handoff §二.10）。口径写清楚，免得现场被问住：
 * 1. 必须再验一次密码，防止 token 被人拿去清库；
 * 2. 家里只有本人时整户删除，否则只摘掉自己——别人的档案不属于你，不能顺手删；
 * 3. ① 的数据走物理删除（逻辑删除只是隐藏，不算删除）；②③ 的数据由各自模块监听事件自己清；
 * 4. audit_log 保留：它是"谁在什么时候删过"的唯一凭证，且内容已脱敏、不含病历原文。
 */
@FamilyModule
@Service
public class DataErasureService {

    public record ErasureReport(boolean familyDeleted, int deletedMembers, int deletedDrugs,
                                int deletedAttachments, String note) {
    }

    private final SysUserMapper userMapper;
    private final FamilyMemberMapper memberMapper;
    private final DrugDictMapper drugDictMapper;
    private final DataErasureSql sql;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    /** 附件表也属于 ①，但挂在 app.module.drug 开关下，用 ObjectProvider 避免硬绑。 */
    private final ObjectProvider<DrugAttachmentService> attachmentServices;

    public DataErasureService(SysUserMapper userMapper,
                              FamilyMemberMapper memberMapper,
                              DrugDictMapper drugDictMapper,
                              DataErasureSql sql,
                              PasswordEncoder passwordEncoder,
                              ApplicationEventPublisher events,
                              ObjectProvider<DrugAttachmentService> attachmentServices) {
        this.userMapper = userMapper;
        this.memberMapper = memberMapper;
        this.drugDictMapper = drugDictMapper;
        this.sql = sql;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.attachmentServices = attachmentServices;
    }

    @Transactional
    public ErasureReport eraseEverything(Long userId, String password) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.FORBIDDEN, "密码不正确，已取消删除");
        }

        List<FamilyMemberEntity> mine = memberMapper.selectList(Wrappers.<FamilyMemberEntity>lambdaQuery()
                .eq(FamilyMemberEntity::getUserId, userId));
        List<Long> memberIds = mine.stream().map(FamilyMemberEntity::getId).toList();
        Long familyId = mine.isEmpty() ? null : mine.get(0).getFamilyId();

        boolean familyDeleted = false;
        String note = "已从家庭中摘除本人，其他成员的数据保留";
        if (familyId != null) {
            long totalMembers = memberMapper.selectCount(Wrappers.<FamilyMemberEntity>lambdaQuery()
                    .eq(FamilyMemberEntity::getFamilyId, familyId));
            if (totalMembers <= mine.size()) {
                familyDeleted = true;
                note = "整户删除：家庭、成员与邀请码已物理清除";
            }
        }

        List<Long> drugIds = drugDictMapper.selectList(Wrappers.<DrugDictEntity>lambdaQuery()
                        .eq(DrugDictEntity::getOwnerUserId, userId))
                .stream().map(DrugDictEntity::getId).toList();

        int deletedAttachments = attachmentServices.stream().findFirst()
                .map(service -> service.eraseByUser(userId))
                .orElse(0);

        sql.eraseOwnedDrugs(userId);
        sql.eraseAttachmentsByUploader(userId);
        if (familyId != null) {
            if (familyDeleted) {
                sql.eraseFamily(familyId);
            } else {
                memberIds.forEach(sql::eraseMember);
            }
        }
        sql.eraseUser(userId);

        events.publishEvent(new AccountErasureRequestedEvent(userId, familyId, familyDeleted, memberIds, drugIds));
        return new ErasureReport(familyDeleted, memberIds.size(), drugIds.size(), deletedAttachments, note);
    }
}
