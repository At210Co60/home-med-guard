package com.homemedguard.common.storage;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 说明书图片/附件的本地磁盘存储：data/files/{yyyy}/{MM}/{uuid}.{ext}。
 * 关键点：文件名一律自己生成（UUID），读取时只接受形如 yyyy/MM/uuid.ext 的 key——
 * 用户传上来的原始文件名只用于展示，绝不参与路径拼接，防目录穿越。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Pattern KEY_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f]{32}\\.[a-z]{3,4}$");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");
    private static final Path ROOT = Paths.get("data", "files").toAbsolutePath().normalize();

    public record Stored(String storageKey, long sizeBytes, String contentType) {
    }

    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件为空");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "只支持 " + String.join("/", ALLOWED_EXTENSIONS) + "，收到 ." + extension);
        }
        LocalDate today = LocalDate.now();
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + name;
        try {
            Path target = ROOT.resolve(key).normalize();
            if (!target.startsWith(ROOT)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "非法存储路径");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new Stored(key, Files.size(target), file.getContentType());
        } catch (IOException ex) {
            log.error("附件保存失败 key={}", key, ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "附件保存失败");
        }
    }

    public Resource load(String storageKey) {
        if (storageKey == null || !KEY_PATTERN.matcher(storageKey).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存储 key 不合法");
        }
        Path path = ROOT.resolve(storageKey).normalize();
        if (!path.startsWith(ROOT) || !Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return new FileSystemResource(path);
    }

    /** 删除失败只记日志：数据已经清了，不该因为磁盘上的孤儿文件回滚整个注销事务。 */
    public void deleteQuietly(String storageKey) {
        if (storageKey == null || !KEY_PATTERN.matcher(storageKey).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(ROOT.resolve(storageKey).normalize());
        } catch (IOException ex) {
            log.warn("附件文件删除失败 key={} cause={}", storageKey, ex.getMessage());
        }
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
