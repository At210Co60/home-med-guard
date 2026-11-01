package com.homemedguard.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.common.contract.DrugQueryFacade;
import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.config.ModuleProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 骨架自检接口：联调和答辩时用来当场证明"模块可单独关掉、契约未接入系统照样起"。
 */
@Tag(name = "00-底座")
@RestController
@RequestMapping("/api")
public class ModuleStatusController {

    public record SkeletonView(List<String> enabledModules,
                               List<String> disabledModules,
                               List<String> unwiredContracts) {
    }

    private final ModuleProperties moduleProperties;
    private final ObjectProvider<MemberProfileQueryFacade> memberFacade;
    private final ObjectProvider<DrugQueryFacade> drugFacade;

    public ModuleStatusController(ModuleProperties moduleProperties,
                                  ObjectProvider<MemberProfileQueryFacade> memberFacade,
                                  ObjectProvider<DrugQueryFacade> drugFacade) {
        this.moduleProperties = moduleProperties;
        this.memberFacade = memberFacade;
        this.drugFacade = drugFacade;
    }

    @GetMapping("/modules")
    public Result<SkeletonView> modules() {
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        moduleProperties.getModule().forEach((name, on) ->
                (Boolean.TRUE.equals(on) ? enabled : disabled).add(name));

        List<String> unwired = new ArrayList<>();
        if (memberFacade.getIfAvailable() == null) {
            unwired.add("MemberProfileQueryFacade");
        }
        if (drugFacade.getIfAvailable() == null) {
            unwired.add("DrugQueryFacade");
        }
        return Result.ok(new SkeletonView(enabled.stream().sorted().toList(), disabled.stream().sorted().toList(), unwired));
    }
}
