package com.example.banking.account.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void customerAccountStartsActiveWithZeroBalance() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        assertEquals("12345678901234", account.getAccountNumber());
        assertEquals(Long.valueOf(42L), account.getCustomerId());
        assertEquals(AccountRole.CUSTOMER, account.getAccountRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(
                0,
                account.getBalance().compareTo(BigDecimal.ZERO)
        );
    }

    @Test
    void systemAccountHasNoCustomer() {
        Account account = Account.openSystemAccount(
                "99999999999999"
        );

        assertNull(account.getCustomerId());
        assertEquals(AccountRole.SYSTEM, account.getAccountRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    void rejectsAccountNumberWithWrongLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Account.openCustomerAccount("12345", 42L)
        );
    }

    @Test
    void rejectsAccountNumberContainingLetters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Account.openCustomerAccount(
                        "1234567890123A",
                        42L
                )
        );
    }

    @Test
    void rejectsInvalidCustomerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Account.openCustomerAccount(
                        "12345678901234",
                        0L
                )
        );
    }

    @Test
    void creditIncreasesBalance() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        account.credit(new BigDecimal("150.2500"));

        assertEquals(
                0,
                account.getBalance().compareTo(
                        new BigDecimal("150.2500")
                )
        );
    }

    @Test
    void debitReducesBalance() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        account.credit(new BigDecimal("200.0000"));
        account.debit(new BigDecimal("75.5000"));

        assertEquals(
                0,
                account.getBalance().compareTo(
                        new BigDecimal("124.5000")
                )
        );
    }

    @Test
    void cannotDebitMoreThanAvailableBalance() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        assertThrows(
                IllegalStateException.class,
                () -> account.debit(new BigDecimal("1.0000"))
        );
    }

    @Test
    void inactiveAccountCannotReceiveCredit() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        account.changeStatus(AccountStatus.INACTIVE);

        assertThrows(
                IllegalStateException.class,
                () -> account.credit(new BigDecimal("10.00"))
        );
    }

    @Test
    void blockedAccountCannotBeDebited() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        account.credit(new BigDecimal("100.00"));
        account.changeStatus(AccountStatus.BLOCKED);

        assertThrows(
                IllegalStateException.class,
                () -> account.debit(new BigDecimal("10.00"))
        );
    }

    @Test
    void rejectsAmountWithMoreThanFourDecimalPlaces() {
        Account account = Account.openCustomerAccount(
                "12345678901234",
                42L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> account.credit(new BigDecimal("10.12345"))
        );
    }
}