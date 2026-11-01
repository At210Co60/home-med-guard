package com.homemedguard.service.drug;

/**
 * 药品录入参数。approvalNo 允许留空——不知道就别编，前端会提示"批准文号未填"。
 */
public record DrugSpec(
        String genericName,
        String tradeName,
        String dosageForm,
        String specification,
        String manufacturer,
        String approvalNo,
        String otcRx) {
}
