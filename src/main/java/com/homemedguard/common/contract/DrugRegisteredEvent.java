package com.homemedguard.common.contract;

/**
 * ① 新增药品后广播；② 监听并对名称/剂型做成分归一化，写入自己拥有的 drug_component。
 * 发送方不关心有没有人消费。
 *
 * @param source DICT / BARCODE / USER_INPUT，USER_INPUT（用户自建药）归一化失败率最高，② 侧要落到"待人工确认"
 */
public record DrugRegisteredEvent(
        Long drugDictId,
        Long familyId,
        String genericName,
        String tradeName,
        String dosageForm,
        String source) {
}
