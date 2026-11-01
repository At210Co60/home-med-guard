package com.homemedguard.service.family;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.service.family.entity.FamilyMemberProfileEntity;
import com.homemedguard.service.family.mapper.FamilyMemberProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 成员档案读写。列表视图不给病历原文，详情视图才解密返回。 */
@FamilyModule
public class MemberProfileService {

    private final FamilyMemberProfileMapper profileMapper;

    public MemberProfileService(FamilyMemberProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public Optional<FamilyMemberProfileEntity> profileOf(Long memberId) {
        return Optional.ofNullable(profileMapper.selectOne(Wrappers.<FamilyMemberProfileEntity>lambdaQuery()
                .eq(FamilyMemberProfileEntity::getMemberId, memberId)
                .last("limit 1")));
    }

    @Transactional
    public FamilyMemberProfileEntity saveProfile(Long memberId, ProfileSpec spec) {
        FamilyMemberProfileEntity entity = profileOf(memberId).orElseGet(FamilyMemberProfileEntity::new);
        entity.setMemberId(memberId);
        entity.setDisplayName(trimToNull(spec.displayName()));
        entity.setBirthDate(spec.birthDate());
        entity.setGender(trimToNull(spec.gender()));
        entity.setWeightKg(spec.weightKg());
        entity.setTagCodes(joinTags(spec.tagCodes()));
        entity.setAllergyHistory(trimToNull(spec.allergyHistory()));
        entity.setChronicConditions(trimToNull(spec.chronicConditions()));
        entity.setLiverGrade(normalizeGrade(spec.liverGrade()));
        entity.setRenalEgfr(spec.renalEgfr());
        if (entity.getId() == null) {
            profileMapper.insert(entity);
        } else {
            profileMapper.updateById(entity);
        }
        return entity;
    }

    public static Set<String> parseTags(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String joinTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String normalizeGrade(String grade) {
        return grade == null || grade.isBlank() ? null : grade.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
