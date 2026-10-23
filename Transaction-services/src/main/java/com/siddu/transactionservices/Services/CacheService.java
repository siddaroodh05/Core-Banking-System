package com.siddu.transactionservices.Services;

import com.siddu.dto.account.Request.AccountIdentifier;
import com.siddu.dto.account.Response.AccountIdentifierResponse;
import com.siddu.dto.transfer.Request.TransactionValidationRequest;
import com.siddu.dto.transfer.Response.TransactionValidationResponse;
import com.siddu.transactionservices.Client.AccountClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheService {
    private final AccountClient accountClient;
    @Autowired
    public CacheService(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @Cacheable(
            cacheNames = "transactionValidation",
            key = "#request.userId() + ':' + " +
                    "#request.senderAccountNumber() + ':' + " +
                    "#request.receiverAccountNumber()"
    )
    public TransactionValidationResponse validateTransaction(
            TransactionValidationRequest request) {

        return accountClient.transactionValidation(request);
    }


    @Cacheable(
            cacheNames = "OwnershipValidation",
            key = "#request.userId() + ':' + #request.AccountNumber()"
    )
    public AccountIdentifierResponse checkOwnership(
            AccountIdentifier request) {

        return accountClient.checkOwnership(request);
    }
}
