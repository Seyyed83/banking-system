package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.dto.TransactionResponse;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionType;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSubmissionServiceTest {

    @Mock
    private TransactionRequestRepository requestRepository;

    @Mock
    private AccountRepository accountRepository;

    private TransactionSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new TransactionSubmissionService(requestRepository, accountRepository);
    }

    private Account account(Long id, String accountNumber, AccountStatus status) {
        Account account = Account.openCustomerAccount(accountNumber, 10L + id);
        ReflectionTestUtils.setField(account, "id", id);
        account.changeStatus(status);
        return account;
    }

    @Test
    void rejectsSubmissionWhenSourceAccountIsBlocked() {
        Account blockedSource = account(1L, "11111111111111", AccountStatus.BLOCKED);
        when(accountRepository.findByAccountNumber("11111111111111"))
                .thenReturn(Optional.of(blockedSource));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "client-1",
                "idem-1",
                TransactionType.WITHDRAWAL,
                "11111111111111",
                null,
                new BigDecimal("100.00")
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> submissionService.submit(request)
        );
        assertTrue(ex.getMessage().contains("BLOCKED"));
        verifyNoInteractions(requestRepository);
    }

    @Test
    void rejectsTransferWhenDestinationAccountIsInactive() {
        Account activeSource = account(1L, "11111111111111", AccountStatus.ACTIVE);
        Account inactiveDest = account(2L, "22222222222222", AccountStatus.INACTIVE);

        when(accountRepository.findByAccountNumber("11111111111111"))
                .thenReturn(Optional.of(activeSource));
        when(accountRepository.findByAccountNumber("22222222222222"))
                .thenReturn(Optional.of(inactiveDest));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "client-1",
                "idem-2",
                TransactionType.TRANSFER,
                "11111111111111",
                "22222222222222",
                new BigDecimal("500.00")
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> submissionService.submit(request)
        );
        assertTrue(ex.getMessage().contains("INACTIVE"));
        verifyNoInteractions(requestRepository);
    }

    @Test
    void populatesHumanReadableFailureMessageInTransactionResponse() {
        TransactionRequest failedRequest = new TransactionRequest(
                "REQ-123",
                "client-1",
                "idem-3",
                TransactionType.TRANSFER,
                1L,
                2L,
                new BigDecimal("1000.00")
        );
        failedRequest.markFailed("INSUFFICIENT_FUNDS");

        TransactionResponse response = TransactionResponse.from(failedRequest, List.of());
        assertEquals("INSUFFICIENT_FUNDS", response.failureCode());
        assertNotNull(response.failureMessage());
        assertTrue(response.failureMessage().toLowerCase().contains("insufficient"));
    }
}
