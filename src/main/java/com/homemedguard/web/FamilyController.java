package com.homemedguard.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.family.FamilyModule;
import com.homemedguard.service.family.FamilyService;
import com.homemedguard.service.family.MemberProfileService;
import com.homemedguard.service.family.ProfileSpec;
import com.homemedguard.service.family.entity.FamilyEntity;
import com.homemedguard.service.family.entity.FamilyMemberEntity;
import com.homemedguard.service.family.entity.FamilyMemberProfileEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 家庭组与成员档案。列表视图不给病历原文（过敏史、慢病），只有成员详情接口才返回，
 * 这是 handoff §二.1"列表页脱敏 VO"的落点。
 */
@Tag(name = "02-家庭与成员")
@FamilyModule
@RestController
@RequestMapping("/api/family")
public class FamilyController {

    public record MemberView(Long memberId, String displayName, Integer ageYears, String relation,
                             String role, boolean profileReady) {
    }

    public record FamilyView(Long familyId, String name, String myRole, List<MemberView> members) {
    }

    public record InviteView(String code, String expiresInSeconds) {
    }

    /** 详情视图：只有家庭内成员能取到自己家的档案。 */
    public record ProfileView(Long memberId, String displayName, LocalDate birthDate, String gender,
                              BigDecimal weightKg, Set<String> tagCodes, String allergyHistory,
                              String chronicConditions, String liverGrade, Integer renalEgfr) {
    }

    public record NewMemberForm(String relation, ProfileSpec profile) {
    }

    private final FamilyService familyService;
    private final MemberProfileService profileService;

    public FamilyController(FamilyService familyService, MemberProfileService profileService) {
        this.familyService = familyService;
        this.profileService = profileService;
    }

    @Operation(summary = "我的家庭与成员列表（脱敏）")
    @GetMapping
    public Result<FamilyView> myFamily() {
        Long userId = SessionUser.requireId();
        FamilyEntity family = familyService.requireFamilyOf(userId);
        String myRole = familyService.requireMembership(userId).getRole();
        List<MemberView> members = familyService.listMembers(family.getId()).stream()
                .map(member -> profileService.profileOf(member.getId())
                        .map(profile -> toView(member, profile))
                        .orElseGet(() -> toView(member, null)))
                .toList();
        return Result.ok(new FamilyView(family.getId(), family.getName(), myRole, members));
    }

    @Operation(summary = "生成家庭邀请码（24 小时有效，一次使用）")
    @PostMapping("/invite")
    public Result<InviteView> invite() {
        return Result.ok(new InviteView(familyService.issueInviteCode(SessionUser.requireId()), "86400"));
    }

    @Operation(summary = "用邀请码加入家庭")
    @PostMapping("/join")
    public Result<FamilyView> join(@RequestBody JoinForm form) {
        Long userId = SessionUser.requireId();
        FamilyEntity family = familyService.joinByCode(userId, form.code());
        return Result.ok(new FamilyView(family.getId(), family.getName(),
                familyService.requireMembership(userId).getRole(), List.of()));
    }

    @Operation(summary = "新增无账号成员（老人/儿童），同时写档案")
    @PostMapping("/members")
    public Result<ProfileView> addMember(@RequestBody NewMemberForm form) {
        FamilyMemberEntity member = familyService.addMember(SessionUser.requireId(), form.relation());
        FamilyMemberProfileEntity profile = profileService.saveProfile(member.getId(), form.profile());
        return Result.ok(toProfileView(member.getId(), profile));
    }

    @Operation(summary = "更新成员档案（健康字段加密入库）")
    @PutMapping("/members/{memberId}/profile")
    public Result<ProfileView> updateProfile(@PathVariable Long memberId, @RequestBody ProfileSpec spec) {
        Long userId = SessionUser.requireId();
        familyService.requireMemberOf(familyService.requireMembership(userId).getFamilyId(), memberId);
        return Result.ok(toProfileView(memberId, profileService.saveProfile(memberId, spec)));
    }

    @Operation(summary = "查看成员档案详情")
    @GetMapping("/members/{memberId}/profile")
    public Result<ProfileView> profile(@PathVariable Long memberId) {
        Long userId = SessionUser.requireId();
        familyService.requireMemberOf(familyService.requireMembership(userId).getFamilyId(), memberId);
        FamilyMemberProfileEntity profile = profileService.profileOf(memberId)
                .orElseThrow(() -> new com.homemedguard.common.error.BizException(
                        com.homemedguard.common.api.ErrorCode.NOT_FOUND, "该成员还没有建档"));
        return Result.ok(toProfileView(memberId, profile));
    }

    public record JoinForm(String code) {
    }

    private static MemberView toView(FamilyMemberEntity member, FamilyMemberProfileEntity profile) {
        Integer ageYears = profile == null || profile.getBirthDate() == null ? null
                : (int) ChronoUnit.YEARS.between(profile.getBirthDate(), LocalDate.now());
        return new MemberView(member.getId(), profile == null ? null : profile.getDisplayName(),
                ageYears, member.getRelation(), member.getRole(), profile != null);
    }

    private static ProfileView toProfileView(Long memberId, FamilyMemberProfileEntity profile) {
        return new ProfileView(memberId, profile.getDisplayName(), profile.getBirthDate(), profile.getGender(),
                profile.getWeightKg(), MemberProfileService.parseTags(profile.getTagCodes()),
                profile.getAllergyHistory(), profile.getChronicConditions(), profile.getLiverGrade(),
                profile.getRenalEgfr());
    }
}
