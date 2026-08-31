package com.resumecraft.server;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骨架验证接口：返回服务基本信息，用于确认应用已启动。
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "service", "resume-craft-server",
                "status", "ok",
                "time", OffsetDateTime.now().toString()
        );
    }
}