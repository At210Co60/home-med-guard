package com.homemedguard.service.family;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 注销专用的物理删除语句。
 * MyBatis-Plus 的 deleteById 走的是逻辑删除（deleted=1），数据行还在表里——
 * 那对"查询看不到"够用，对"删除我的全部数据"不够用，所以这里按表主键/归属列做真实 DELETE。
 * 全部用 ? 占位参数，不拼字符串。
 */
@FamilyModule
@Service
public class DataErasureSql {

    private final JdbcTemplate jdbc;

    public DataErasureSql(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void eraseOwnedDrugs(Long userId) {
        jdbc.update("DELETE FROM drug_barcode WHERE drug_dict_id IN "
                + "(SELECT id FROM drug_dict WHERE owner_user_id = ?)", userId);
        jdbc.update("DELETE FROM drug_dict WHERE owner_user_id = ?", userId);
    }

    public void eraseAttachmentsByUploader(Long userId) {
        jdbc.update("DELETE FROM drug_attachment WHERE uploaded_by = ?", userId);
    }

    public void eraseMember(Long memberId) {
        jdbc.update("DELETE FROM family_member_profile WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM family_member WHERE id = ?", memberId);
    }

    public void eraseFamily(Long familyId) {
        jdbc.update("DELETE FROM family_invite WHERE family_id = ?", familyId);
        jdbc.update("DELETE FROM family_member_profile WHERE member_id IN "
                + "(SELECT id FROM family_member WHERE family_id = ?)", familyId);
        jdbc.update("DELETE FROM family_member WHERE family_id = ?", familyId);
        jdbc.update("DELETE FROM family WHERE id = ?", familyId);
    }

    public void eraseUser(Long userId) {
        jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
    }
}
