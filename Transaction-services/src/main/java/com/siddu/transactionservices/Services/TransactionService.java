package com.siddu.transactionservices.Services;

import com.siddu.Enums.CheckOwnerShip;
import com.siddu.Enums.TransferStatus;
import com.siddu.dto.account.Request.AccountIdentifier;
import com.siddu.dto.account.Response.AccountIdentifierResponse;
import com.siddu.dto.transfer.Request.AccountTransferRequest;
import com.siddu.dto.transfer.Request.TransactionValidationRequest;
import com.siddu.dto.transfer.Response.AccountTransferResponse;
import com.siddu.dto.transfer.Response.TransactionValidationResponse;
import com.siddu.transactionservices.Client.AccountClient;
import com.siddu.transactionservices.Dto.Requests.AccountNumberRequest;
import com.siddu.transactionservices.Dto.Requests.TransferMoneyRequest;
import com.siddu.transactionservices.Dto.Response.AccessForbiddenResponse;
import com.siddu.transactionservices.Dto.Response.AccountTransactionParty;
import com.siddu.transactionservices.Dto.Response.TransferMoneyResponse;
import com.siddu.transactionservices.Entity.TransactionEntity;
import com.siddu.transactionservices.Enums.TransactionStatus;
import com.siddu.transactionservices.Exceptions.*;
import com.siddu.transactionservices.Repository.TransactionEntityRepository;
import com.siddu.transactionservices.Repository.TransactionHistoryRow;
import com.siddu.transactionservices.Utils.SecurityUtils;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionEntityRepository transactionEntityRepository;
    private final AccountClient accountClient;
    private final PendingTransactionService pendingTransactionService;
    private final FailedTransactionService failedTransactionService;
    private final SuccessTransactionService successTransactionService;
    private final IdempotencyService idempotencyService;


    public TransactionService(TransactionEntityRepository transactionEntityRepository,
                              AccountClient accountClient,
                              PendingTransactionService pendingTransactionService,
                              FailedTransactionService failedTransactionService,
                              SuccessTransactionService successTransactionService,
                              IdempotencyService idempotencyService) {
        this.transactionEntityRepository = transactionEntityRepository;
        this.accountClient = accountClient;
        this.pendingTransactionService = pendingTransactionService;
        this.failedTransactionService = failedTransactionService;
        this.successTransactionService = successTransactionService;
        this.idempotencyService = idempotencyService;

    }



    public TransferMoneyResponse transferMoney(TransferMoneyRequest request) {

        TransferMoneyResponse existTransaction =idempotencyService.findByIdempotencyKey(request.idempotencyKey());

        if (existTransaction != null) {

            if (!request.senderAccountNumber().equals(
                    existTransaction.debitedFrom().accountNumber())) {
                throw new AccessForbiddenException(
                        new AccessForbiddenResponse(
                                "You are not authorized to access this transaction"));
            }

            return existTransaction;
        }

        TransactionValidationResponse validationResponse=accountClient.transactionValidation(
                new TransactionValidationRequest(
                        SecurityUtils.getCurrentUserId(),
                        request.senderAccountNumber(),
                        request.receiverAccountNumber()
                ));

        TransactionEntity transaction;
        try {
            transaction = pendingTransactionService.savePendingTransaction(request, validationResponse);
            idempotencyService.cacheTransactionResponse(transaction.getIdempotencyKey(),transaction);
        }
        catch (DataIntegrityViolationException e) {
            return fetchExistingTransaction(request.senderAccountNumber(),request.idempotencyKey());
        }

        AccountTransferResponse response;

        try {


             response =
                    accountClient.transfer(
                            new AccountTransferRequest(
                                    transaction.getId(),
                                    request.senderAccountNumber(),
                                    request.receiverAccountNumber(),
                                    request.amount(),
                                    request.transactionPin()
                            )
                    );
        }
        catch (RetryableException ex){
            pendingTransactionService.markAccountServiceUnavailable(transaction);
            return pendingResponse(transaction);
        }
        catch (FeignException.InternalServerError |
               FeignException.BadGateway |
               FeignException.ServiceUnavailable |
               FeignException.GatewayTimeout ex){

           pendingTransactionService.markUnknownOutcome(transaction);
            return pendingResponse(transaction);
        }

        catch(FeignException ex){
          failedTransactionService.markAccountServiceError(transaction);
            throw ex;
        }


        if(response.status() == TransferStatus.FAILED){
            failedTransactionService.handleFailedTransaction(response,transaction);
            idempotencyService.cacheTransactionResponse(transaction.getIdempotencyKey(),transaction);
        }

        TransferMoneyResponse Successresponse= successTransactionService.saveSuccessTransaction(transaction);
        idempotencyService.cacheTransactionResponse(transaction.getIdempotencyKey(),transaction);
        return  Successresponse;

    }

    public TransferMoneyResponse fetchExistingTransaction(
            String senderAccountNumber,
            UUID idempotencyKey) {

        TransferMoneyResponse response = idempotencyService.findByIdempotencyKey(idempotencyKey);

        if (!response.debitedFrom().accountNumber().equals(senderAccountNumber)) {
            throw new AccessForbiddenException(
                    new AccessForbiddenResponse(
                            "you are not authorized to access transaction"));
        }

        return response;
    }

    private TransferMoneyResponse pendingResponse(TransactionEntity transaction) {
        return new TransferMoneyResponse(
                transaction.getTransactionId(),
                new AccountTransactionParty(transaction.getSnapshot().getSourceAccountHolderName(),
                        transaction.getSourceAccountNumber()),
                new AccountTransactionParty( transaction.getSnapshot().getDestinationAccountHolderName()
                        ,transaction.getDestinationAccountNumber()),
                transaction.getAmount(),
                TransactionStatus.PENDING,
                transaction.getFailureReason(),
                transaction.getCreatedAt()
        );


    }



    public Page<TransferMoneyResponse> getTransactionshistory(int page, int size, AccountNumberRequest request){

        AccountIdentifierResponse Ownership=accountClient.checkOwnership(new AccountIdentifier(
                SecurityUtils.getCurrentUserId(),
                request.accountNumber()));


        if(Ownership.owner().equals(CheckOwnerShip.INVALID_OWNER)){
            throw new AccessForbiddenException(new AccessForbiddenResponse("you are not authorized to access this account"));

        }

        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());

        Page<TransactionHistoryRow> history=transactionEntityRepository.findAccountTransactions(request.accountNumber(),pageable);

        return history.map(txn ->
                    new TransferMoneyResponse(
                            txn.getTransactionId(),
                            new AccountTransactionParty(
                                    txn.getDebitedFromAccountHolderName(),
                                    txn.getDebitedFromAccountNumber()
                            ),
                            new AccountTransactionParty(
                                    txn.getCreditedToAccountHolderName(),
                                    txn.getCreditedToAccountNumber()
                            ),
                            txn.getAmount(),
                            txn.getStatus(),
                            txn.getFailureReason(),
                            txn.getCompletedAt()
                    ));

    }
}




