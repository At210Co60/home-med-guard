package com.homemedguard.service.family.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.contract.MemberContextDTO;
import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.service.family.FamilyModule;
import com.homemedguard.service.family.MemberProfileService;
import com.homemedguard.service.family.entity.FamilyMemberEntity;
import com.homemedguard.service.family.entity.FamilyMemberProfileEntity;
import com.homemedguard.service.family.mapper.FamilyMemberMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * ① 实现给 ②③ 的只读契约。
 * 只吐判定必需的最小字段；过敏史与慢病按原文（逗号/顿号切分）交出，归一化由 ② 做。
 */
@FamilyModule
public class MemberProfileQueryFacadeImpl implements MemberProfileQueryFacade {

    private final FamilyMemberMapper memberMapper;
    private final MemberProfileService profileService;

    public MemberProfileQueryFacadeImpl(FamilyMemberMapper memberMapper, MemberProfileService profileService) {
        this.memberMapper = memberMapper;
        this.profileService = profileService;
    }

    @Override
    public Optional<MemberContextDTO> memberContext(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        FamilyMemberEntity member = memberMapper.selectById(memberId);
        if (member == null) {
            return Optional.empty();
        }
        return profileService.profileOf(memberId).map(profile -> toContext(member, profile));
    }

    @Override
    public List<MemberContextDTO> familyMemberContexts(Long familyId) {
        if (familyId == null) {
            return List.of();
        }
        List<FamilyMemberEntity> members = memberMapper.selectList(Wrappers.<FamilyMemberEntity>lambdaQuery()
                .eq(FamilyMemberEntity::getFamilyId, familyId));
        return members.stream()
                .map(member -> profileService.profileOf(member.getId()).map(profile -> toContext(member, profile)))
                .flatMap(Optional::stream)
                .toList();
    }

    private static MemberContextDTO toContext(FamilyMemberEntity member, FamilyMemberProfileEntity profile) {
        return new MemberContextDTO(member.getId(), member.getFamilyId(), profile.getDisplayName(),
                profile.getBirthDate(), profile.getWeightKg(),
                MemberProfileService.parseTags(profile.getTagCodes()),
                splitTerms(profile.getAllergyHistory()),
                profile.getLiverGrade(), profile.getRenalEgfr());
    }

    private static List<String> splitTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[，,、;；/]"))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .toList();
    }
}
