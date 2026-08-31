package com.resumecraft.server.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置（从 application.yml 读取）。
 * 
 * 你可以在 application.yml 里配置：
 *   app.storage.upload-dir=uploads
 *   app.storage.max-file-size=10MB
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    /** 上传文件存储目录（相对于工作目录或绝对路径） */
    private String uploadDir = "uploads";

}