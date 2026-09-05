package com.resumecraft.server.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.application.domain.ApplicationRecord;
import com.resumecraft.server.application.domain.ApplicationRecordMapper;
import com.resumecraft.server.application.service.ApplicationRecordService;
import com.resumecraft.server.common.security.AuthContext;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ApplicationRecordServiceImpl implements ApplicationRecordService {

    @Resource
    private ApplicationRecordMapper applicationRecordMapper;

    /**
     * 创建投递记录
     * @param record
     * @return
     */
    @Override
    @Transactional
    public ApplicationRecord create(ApplicationRecord record) {

        //获取当前用户ID
        Long userId = AuthContext.getUserId();
        record.setUserId(userId);
        applicationRecordMapper.insert(record);
        return record;
    }

    /**
     *  查询所有投递记录（按ID倒序）
     * @return
     */
    @Override
    public List<ApplicationRecord> findall() {

        Long userId = AuthContext.getUserId();

        LambdaQueryWrapper<ApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationRecord::getUserId, userId)  // ← 添加用户过滤
                .orderByDesc(ApplicationRecord::getId);
        return applicationRecordMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询投递记录
     * @param id
     * @return
     */
    @Override
    public ApplicationRecord findById(Long id) {

        Long userId = AuthContext.getUserId();
        LambdaQueryWrapper<ApplicationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationRecord::getUserId, userId).eq(ApplicationRecord::getId, id);

        ApplicationRecord record = applicationRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new IllegalArgumentException("投递记录不存在: id=" + id);
        }
        return record;

    }

    /**
     *  更新投递记录
     * @param id
     * @param record
     * @return
     */
    @Override
    @Transactional
    public ApplicationRecord update(Long id,ApplicationRecord record) {

        // 1. 先确认记录存在
        ApplicationRecord record1 = findById(id);
        record.setId(id);
        applicationRecordMapper.updateById(record);

        return findById(id);
    }

    /**
     * 删除记录
     * @param id
     * @return
     */
    @Override
    public void delete(Long id) {
        // 1. 先确认记录存在
        findById(id);
        // 2. 删除
        applicationRecordMapper.deleteById(id);

        log.info("删除投递记录成功: id={}", id);
    }
}
