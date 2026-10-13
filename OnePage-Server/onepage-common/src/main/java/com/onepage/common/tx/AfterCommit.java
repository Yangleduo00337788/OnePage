package com.onepage.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后再跑副作用（Feign 投递通知）。
 * 放在事务里打下游会拉长锁；下游挂了也不该回滚赞藏/关注。
 */
public final class AfterCommit {
  private AfterCommit() {}

  public static void run(Runnable task) {
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          task.run();
        }
      });
      return;
    }
    task.run();
  }
}
