package com.resumecraft.server.application.controller;

import com.resumecraft.server.application.domain.ApplicationRecord;
import com.resumecraft.server.application.service.ApplicationRecordService;
import com.resumecraft.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/application")
public class ApplicationRecordController {

    @Resource
    private ApplicationRecordService applicationRecordService;

    /**
     * 创建投递记录
     * @param record
     * @return
     */
    @PostMapping
    public ApiResponse<ApplicationRecord> createApplicationRecord(@Valid @RequestBody ApplicationRecord record) {

        return ApiResponse.ok(applicationRecordService.create(record));
    }

    /**
     *  查询所有投递记录（按ID倒序）
     * @return
     */
    @GetMapping
    public ApiResponse<List<ApplicationRecord>> findAllApplicationRecord() {
        return ApiResponse.ok(applicationRecordService.findall());
    }

    /**
     * 根据ID查询投递记录
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ApiResponse<ApplicationRecord> findById(@Valid @PathVariable Long id) {
        return ApiResponse.ok(applicationRecordService.findById(id));
    }

    /**
     *  更新投递记录
     * @param id
     * @param record
     * @return
     */
    @PutMapping("/{id}")
    public ApiResponse<ApplicationRecord> updateApplicationRecord(@Valid @PathVariable Long id,@Valid @RequestBody ApplicationRecord record) {
        return ApiResponse.ok(applicationRecordService.update(id,record));
    }

    /**
     * 删除投递记录
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteApplicationRecord(@Valid @PathVariable Long id) {
        applicationRecordService.delete(id);

        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    @Operation(summary = "获取投递统计", description = "按状态分组统计当前用户的投递数量，包含总数")
    public ApiResponse<Map<String, Long>> stats() {
        log.info("获取投递统计");
        Map<String, Long> result = applicationRecordService.stats();
        return ApiResponse.ok(result);
    }
}
