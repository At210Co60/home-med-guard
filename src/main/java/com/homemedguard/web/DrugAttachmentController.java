package com.homemedguard.web;

import com.homemedguard.common.api.Result;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.service.auth.core.LoginUser;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.drug.DrugAttachmentService;
import com.homemedguard.service.drug.DrugModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "04-药品字典")
@DrugModule
@RestController
public class DrugAttachmentController {

    private final DrugAttachmentService attachmentService;

    public DrugAttachmentController(DrugAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Operation(summary = "上传说明书图片/PDF（jpg/jpeg/png/webp/pdf，走 spring.servlet.multipart 限制）")
    @AuditLog(action = "DRUG_ATTACHMENT_UPLOAD", targetType = "drug_attachment")
    @PostMapping(value = "/api/drugs/{drugDictId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DrugAttachmentService.AttachmentView> upload(@PathVariable Long drugDictId,
                                                               @RequestParam("file") MultipartFile file,
                                                               @RequestParam(value = "kind", required = false) String kind) {
        LoginUser user = SessionUser.require();
        return Result.ok(attachmentService.upload(drugDictId, kind, file, user.userId(), isSysAdmin(user)));
    }

    @Operation(summary = "列出某药的附件")
    @GetMapping("/api/drugs/{drugDictId}/attachments")
    public Result<List<DrugAttachmentService.AttachmentView>> list(@PathVariable Long drugDictId) {
        LoginUser user = SessionUser.require();
        return Result.ok(attachmentService.listByDrug(drugDictId, user.userId(), isSysAdmin(user)));
    }

    @Operation(summary = "读取附件内容（越权按 404）")
    @GetMapping("/api/drug-attachments/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        LoginUser user = SessionUser.require();
        DrugAttachmentService.DownloadContent download = attachmentService.content(id, user.userId(), isSysAdmin(user));
        MediaType mediaType = download.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(download.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + download.fileName() + "\"")
                .body(download.resource());
    }

    @Operation(summary = "删除附件")
    @AuditLog(action = "DRUG_ATTACHMENT_DELETE", targetType = "drug_attachment")
    @DeleteMapping("/api/drug-attachments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = SessionUser.require();
        attachmentService.delete(id, user.userId(), isSysAdmin(user));
        return Result.ok();
    }

    private static boolean isSysAdmin(LoginUser user) {
        return "SYS_ADMIN".equals(user.role());
    }
}
