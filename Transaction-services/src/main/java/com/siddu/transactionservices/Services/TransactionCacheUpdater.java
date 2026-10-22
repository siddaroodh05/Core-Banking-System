package com.siddu.transactionservices.Services;

import com.siddu.transactionservices.Entity.TransactionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TransactionCacheUpdater {

    private final IdempotencyService idempotencyService;

    public TransactionCacheUpdater(
            IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    public void updateAfterCommit(TransactionEntity transaction) {

        Runnable update = () ->
                idempotencyService.cacheTransactionResponse(
                        transaction.getIdempotencyKey(),
                        transaction
                );

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            update.run();
                        }
                    }
            );
        } else {
            update.run();
        }
    }
}