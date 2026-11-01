package com.homemedguard.service.drug;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.common.storage.FileStorageService;
import com.homemedguard.service.drug.entity.DrugAttachmentEntity;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import com.homemedguard.service.drug.mapper.DrugAttachmentMapper;
import com.homemedguard.service.drug.mapper.DrugDictMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 说明书图片上传与读取（handoff §二.2 "说明书图片/附件上传"）。
 * 可见性与 drug_dict 一致：系统字典的附件人人可看，自建药的附件只有上传者能看。
 */
@DrugModule
@Service
public class DrugAttachmentService {

    public record AttachmentView(Long id, Long drugDictId, String kind, String originalName,
                                 String contentType, long sizeBytes, String createdAt) {
    }

    private final DrugAttachmentMapper attachmentMapper;
    private final DrugDictMapper drugDictMapper;
    private final FileStorageService storage;

    public DrugAttachmentService(DrugAttachmentMapper attachmentMapper,
                                 DrugDictMapper drugDictMapper,
                                 FileStorageService storage) {
        this.attachmentMapper = attachmentMapper;
        this.drugDictMapper = drugDictMapper;
        this.storage = storage;
    }

    @Transactional
    public AttachmentView upload(Long drugDictId, String kind, MultipartFile file, Long userId, boolean sysAdmin) {
        DrugDictEntity drug = drugDictMapper.selectById(drugDictId);
        if (drug == null || !drug.visibleTo(userId, sysAdmin)) {
            throw new BizException(ErrorCode.NOT_FOUND, "药品不存在或无权访问");
        }
        FileStorageService.Stored stored = storage.store(file);

        DrugAttachmentEntity entity = new DrugAttachmentEntity();
        entity.setDrugDictId(drugDictId);
        entity.setKind(kind == null || kind.isBlank() ? "LEAFLET_IMAGE" : kind.trim().toUpperCase());
        entity.setStorageKey(stored.storageKey());
        entity.setOriginalName(safeName(file.getOriginalFilename()));
        entity.setContentType(stored.contentType());
        entity.setSizeBytes(stored.sizeBytes());
        entity.setUploadedBy(userId);
        attachmentMapper.insert(entity);
        return toView(entity);
    }

    public List<AttachmentView> listByDrug(Long drugDictId, Long userId, boolean sysAdmin) {
        DrugDictEntity drug = drugDictMapper.selectById(drugDictId);
        if (drug == null || !drug.visibleTo(userId, sysAdmin)) {
            throw new BizException(ErrorCode.NOT_FOUND, "药品不存在或无权访问");
        }
        return attachmentMapper.selectList(Wrappers.<DrugAttachmentEntity>lambdaQuery()
                        .eq(DrugAttachmentEntity::getDrugDictId, drugDictId)
                        .orderByDesc(DrugAttachmentEntity::getId))
                .stream().map(this::toView).toList();
    }

    /** 返回"文件 + 响应类型"，调用方直接写流；越权按不存在处理。 */
    public DownloadContent content(Long attachmentId, Long userId, boolean sysAdmin) {
        DrugAttachmentEntity entity = requireVisible(attachmentId, userId, sysAdmin);
        return new DownloadContent(storage.load(entity.getStorageKey()), entity.getContentType(),
                entity.getOriginalName());
    }

    @Transactional
    public void delete(Long attachmentId, Long userId, boolean sysAdmin) {
        DrugAttachmentEntity entity = requireVisible(attachmentId, userId, sysAdmin);
        attachmentMapper.deleteById(entity.getId());
        storage.deleteQuietly(entity.getStorageKey());
    }

    /** 注销时由 ① 自己清（附件表属于 ①），不发给其它模块。 */
    @Transactional
    public int eraseByUser(Long userId) {
        List<DrugAttachmentEntity> rows = attachmentMapper.selectList(Wrappers.<DrugAttachmentEntity>lambdaQuery()
                .eq(DrugAttachmentEntity::getUploadedBy, userId));
        for (DrugAttachmentEntity row : rows) {
            attachmentMapper.deleteById(row.getId());
            storage.deleteQuietly(row.getStorageKey());
        }
        return rows.size();
    }

    public record DownloadContent(Resource resource, String contentType, String fileName) {
    }

    private DrugAttachmentEntity requireVisible(Long attachmentId, Long userId, boolean sysAdmin) {
        DrugAttachmentEntity entity = attachmentMapper.selectById(attachmentId);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        DrugDictEntity drug = drugDictMapper.selectById(entity.getDrugDictId());
        if (drug == null || !drug.visibleTo(userId, sysAdmin)) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return entity;
    }

    private AttachmentView toView(DrugAttachmentEntity entity) {
        return new AttachmentView(entity.getId(), entity.getDrugDictId(), entity.getKind(),
                entity.getOriginalName(), entity.getContentType(),
                entity.getSizeBytes() == null ? 0 : entity.getSizeBytes(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
    }

    /** 原始文件名只用于展示，去掉路径分隔符与控制字符，防止下游把它当路径用。 */
    private static String safeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        String cleaned = originalName.replaceAll("[\\\\/\\r\\n\\t]", "_").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
