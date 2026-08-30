package com.resumecraft.server.application.service;

import com.resumecraft.server.application.domain.ApplicationRecord;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface ApplicationRecordService {
    ApplicationRecord create( ApplicationRecord record);

    List<ApplicationRecord> findall();

    ApplicationRecord findById( Long id);

    ApplicationRecord update(Long id,ApplicationRecord record);

    void delete(Long id);
}
