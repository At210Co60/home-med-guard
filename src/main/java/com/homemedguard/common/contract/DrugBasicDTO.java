package com.homemedguard.common.contract;

/**
 * 药品字典的对外最小视图。成分（component）不在这里——drug_component 归 ② 所有，
 * 由 ② 自己按 drugDictId 查标准成分，避免两边都能写同一张表。
 *
 * @param otcRx OTC / RX，Rx 处方药在风险报告里单独标注来源
 */
public record DrugBasicDTO(
        Long drugDictId,
        String genericName,
        String tradeName,
        String dosageForm,
        String specification,
        String approvalNo,
        String otcRx) {
}
