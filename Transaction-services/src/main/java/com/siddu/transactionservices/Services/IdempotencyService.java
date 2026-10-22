package com.siddu.transactionservices.Services;


import com.siddu.transactionservices.Dto.Response.AccountTransactionParty;
import com.siddu.transactionservices.Dto.Response.TransferMoneyResponse;
import com.siddu.transactionservices.Entity.TransactionEntity;
import com.siddu.transactionservices.Repository.TransactionEntityRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdempotencyService {

    private final TransactionEntityRepository repo;

    public IdempotencyService(TransactionEntityRepository repo) {
        this.repo = repo;
    }

    @Cacheable(
            cacheNames = "txn",
            key = "#idempotencyKey",
            unless = "#result == null"
    )
    public TransferMoneyResponse findByIdempotencyKey(
            UUID idempotencyKey) {

        return repo.findByIdempotencyKey(idempotencyKey)
                .map(t -> new TransferMoneyResponse(
                        t.getTransactionId(),
                        new AccountTransactionParty(
                                t.getSnapshot().getSourceAccountHolderName(),
                                t.getSourceAccountNumber()),
                        new AccountTransactionParty(
                                t.getSnapshot().getDestinationAccountHolderName(),
                                t.getDestinationAccountNumber()),
                        t.getAmount(),
                        t.getStatus(),
                        t.getFailureReason(),
                        t.getCompletedAt()
                ))
                .orElse(null);
    }

    @CachePut(cacheNames = "txn", key = "#idempotencyKey")
    public TransferMoneyResponse cacheTransactionResponse(
            UUID idempotencyKey,
            TransactionEntity transaction) {

        return new TransferMoneyResponse(
                transaction.getTransactionId(),
                new AccountTransactionParty(
                        transaction.getSnapshot().getSourceAccountHolderName(),
                        transaction.getSourceAccountNumber()),
                new AccountTransactionParty(
                        transaction.getSnapshot().getDestinationAccountHolderName(),
                        transaction.getDestinationAccountNumber()),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt()
        );
    }



}
