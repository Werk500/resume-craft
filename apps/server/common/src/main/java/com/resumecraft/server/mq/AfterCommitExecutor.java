package com.resumecraft.server.mq;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行工具。
 *
 * <p>为什么需要它：消息在事务内发出时，消费者可能读到尚未提交的数据。
 * 这是"本地事务 + 消息队列"组合下的经典时序问题。
 */
@Component
public class AfterCommitExecutor {

    public void execute(Runnable task) {
        if (task == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}