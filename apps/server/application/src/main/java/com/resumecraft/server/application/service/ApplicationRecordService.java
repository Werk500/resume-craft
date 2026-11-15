package com.resumecraft.server.application.service;

import com.resumecraft.server.application.domain.ApplicationRecord;

import java.util.List;
import java.util.Map;

public interface ApplicationRecordService {
    ApplicationRecord create( ApplicationRecord record);

    List<ApplicationRecord> findall();

    ApplicationRecord findById( Long id);

    ApplicationRecord updateStatus(Long id, String status);

    void delete(Long id);

    /**
     * 获取投递统计
     * @return
     */
    Map<String, Long> stats();
}
