package com.homemedguard.service.family;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.config.AuthProperties;
import com.homemedguard.service.family.entity.FamilyEntity;
import com.homemedguard.service.family.entity.FamilyInviteEntity;
import com.homemedguard.service.family.entity.FamilyMemberEntity;
import com.homemedguard.service.family.mapper.FamilyInviteMapper;
import com.homemedguard.service.family.mapper.FamilyMapper;
import com.homemedguard.service.family.mapper.FamilyMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 家庭组：建组、邀请码、加入。
 * handoff §二.1 的"一人只在一个默认家庭"在这里用事务 + 前置校验保证，不靠数据库唯一索引猜。
 */
@FamilyModule
public class FamilyService {

    static final String FAMILY_ADMIN = "FAMILY_ADMIN";
    static final String MEMBER = "MEMBER";

    /** 去掉 0/O/1/I，避免手抄邀请码出错 */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper memberMapper;
    private final FamilyInviteMapper inviteMapper;
    private final AuthProperties authProperties;
    private final SecureRandom random = new SecureRandom();

    public FamilyService(FamilyMapper familyMapper,
                         FamilyMemberMapper memberMapper,
                         FamilyInviteMapper inviteMapper,
                         AuthProperties authProperties) {
        this.familyMapper = familyMapper;
        this.memberMapper = memberMapper;
        this.inviteMapper = inviteMapper;
        this.authProperties = authProperties;
    }

    public Optional<FamilyMemberEntity> membershipOf(Long userId) {
        return Optional.ofNullable(memberMapper.selectOne(Wrappers.<FamilyMemberEntity>lambdaQuery()
                .eq(FamilyMemberEntity::getUserId, userId)
                .last("limit 1")));
    }

    public FamilyMemberEntity requireMembership(Long userId) {
        return membershipOf(userId).orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN,
                "当前账号还没有家庭：注册时会自动创建个人家庭，请确认账号状态"));
    }

    /** 注册即建个人家庭：本人是 FAMILY_ADMIN，relation=SELF，之后可以邀请别人进来。 */
    @Transactional
    public FamilyMemberEntity createPersonalFamily(Long userId) {
        if (membershipOf(userId).isPresent()) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "该账号已有家庭，不重复建组");
        }
        FamilyEntity family = insertFamily(userId, "我的家庭");
        return insertMember(family.getId(), userId, FAMILY_ADMIN, "SELF");
    }

    @Transactional
    public FamilyEntity createFamily(Long userId, String name) {
        if (membershipOf(userId).isPresent()) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "一个账号只能在一个家庭；请先退出或加入别人的邀请");
        }
        FamilyEntity family = insertFamily(userId, name);
        insertMember(family.getId(), userId, FAMILY_ADMIN, "SELF");
        return family;
    }

    @Transactional
    public FamilyInviteEntity issueInviteCode(Long userId) {
        FamilyMemberEntity membership = requireMembership(userId);
        if (!FAMILY_ADMIN.equals(membership.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有家庭管理员可以生成邀请码");
        }
        FamilyInviteEntity invite = new FamilyInviteEntity();
        invite.setFamilyId(membership.getFamilyId());
        invite.setCode(randomCode());
        invite.setCreatedBy(userId);
        invite.setExpiresAt(LocalDateTime.now().plus(authProperties.getInviteTtl()));
        inviteMapper.insert(invite);
        return invite;
    }

    /**
     * 用邀请码加入家庭。
     * 因为注册时已经自动建了个人家庭，"加入别人家"是一次迁移：
     * 只有当现有家庭是自己的个人家庭（只有本人一个成员）时才允许迁走，否则一律 409——
     * 把人从有别人的家庭里悄悄摘走会连带影响 ②③ 的数据归属。
     */
    @Transactional
    public FamilyEntity joinByCode(Long userId, String code) {
        FamilyInviteEntity invite = inviteMapper.selectOne(Wrappers.<FamilyInviteEntity>lambdaQuery()
                .eq(FamilyInviteEntity::getCode, code == null ? "" : code.trim().toUpperCase())
                .last("limit 1"));
        if (invite == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "邀请码不存在");
        }
        if (invite.getUsedBy() != null) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "邀请码已被使用");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "邀请码已过期，请让家庭管理员重新生成");
        }

        Optional<FamilyMemberEntity> current = membershipOf(userId);
        if (current.isPresent()) {
            if (current.get().getFamilyId().equals(invite.getFamilyId())) {
                throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT, "你已经在这个家庭里了");
            }
            releasePersonalFamily(current.get(), userId);
        }

        invite.setUsedBy(userId);
        invite.setUsedAt(LocalDateTime.now());
        inviteMapper.updateById(invite);
        insertMember(invite.getFamilyId(), userId, MEMBER, null);
        return familyMapper.selectById(invite.getFamilyId());
    }

    private void releasePersonalFamily(FamilyMemberEntity current, Long userId) {
        FamilyEntity family = familyMapper.selectById(current.getFamilyId());
        if (family == null || !userId.equals(family.getOwnerUserId())
                || !FAMILY_ADMIN.equals(current.getRole())
                || listMembers(current.getFamilyId()).size() != 1) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT,
                    "你已在有其他人的家庭里，请先由该家庭管理员处理后再加入新家庭");
        }
        memberMapper.deleteById(current.getId());
        familyMapper.deleteById(family.getId());
    }

    /** 给家里没账号的人（老人、小孩）建一个成员位，档案随后由 MemberProfileService 写。 */
    @Transactional
    public FamilyMemberEntity addMember(Long userId, String relation) {
        FamilyMemberEntity membership = requireMembership(userId);
        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setFamilyId(membership.getFamilyId());
        member.setRole(MEMBER);
        member.setRelation(relation == null || relation.isBlank() ? null : relation.trim().toUpperCase());
        memberMapper.insert(member);
        return member;
    }

    public List<FamilyMemberEntity> listMembers(Long familyId) {
        return memberMapper.selectList(Wrappers.<FamilyMemberEntity>lambdaQuery()
                .eq(FamilyMemberEntity::getFamilyId, familyId));
    }

    public FamilyEntity requireFamilyOf(Long userId) {
        FamilyMemberEntity membership = requireMembership(userId);
        FamilyEntity family = familyMapper.selectById(membership.getFamilyId());
        if (family == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "家庭不存在");
        }
        return family;
    }

    /** 家庭归属校验：越权访问别人家的成员一律按"不存在"处理，不泄露存在性。 */
    public FamilyMemberEntity requireMemberOf(Long familyId, Long memberId) {
        FamilyMemberEntity member = memberMapper.selectById(memberId);
        if (member == null || !member.getFamilyId().equals(familyId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "成员不存在或不属于当前家庭");
        }
        return member;
    }

    private FamilyEntity insertFamily(Long ownerUserId, String name) {
        FamilyEntity family = new FamilyEntity();
        family.setName(name == null || name.isBlank() ? "我的家庭" : name.trim());
        family.setOwnerUserId(ownerUserId);
        familyMapper.insert(family);
        return family;
    }

    private FamilyMemberEntity insertMember(Long familyId, Long userId, String role, String relation) {
        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        member.setRelation(relation);
        memberMapper.insert(member);
        return member;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}
