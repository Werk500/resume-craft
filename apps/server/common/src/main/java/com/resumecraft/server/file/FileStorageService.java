package com.resumecraft.server.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.resumecraft.server.common.FileStorageProperties;

/**
 * 文件存储服务（MVP 存本地磁盘，M4 后切 MinIO）。
 * 
 * 用法：
 *   String storedPath = fileStorageService.store(inputStream, originalFilename);
 */
@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(FileStorageProperties props) {
        this.uploadDir = Path.of(props.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, e);
        }
    }

    /**
     * 保存文件并返回存储路径（相对路径）。
     * 文件名 = UUID + 原始扩展名，防止冲突。
     */
    public String store(InputStream inputStream, String originalFilename) throws IOException {
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0) {
            ext = originalFilename.substring(dot);
        }
        String storedName = UUID.randomUUID().toString() + ext;
        Path target = uploadDir.resolve(storedName);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    /**
     * 删除已存储的文件（按 store() 返回的相对文件名）。
     *
     * @param storedName 文件名（UUID + 扩展名）
     */
    public void delete(String storedName) throws IOException {
        if (storedName == null || storedName.isEmpty()) {
            return;
        }
        Path target = uploadDir.resolve(storedName).normalize();
        Files.deleteIfExists(target);
    }
}
