package com.minipay.account;

import com.minipay.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests of the money rules, without Spring or a database. */
class AccountTest {

    @Test
    void newAccountStartsWithZeroBalance() {
        Account account = new Account("Alice", "EUR");

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void decimalAmountsAreExact() {
        Account account = new Account("Alice", "EUR");

        account.credit(new BigDecimal("0.10"));
        account.credit(new BigDecimal("0.20"));

        // With double, 0.1 + 0.2 = 0.30000000000000004. BigDecimal keeps it exact.
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void debitReducesBalance() {
        Account account = new Account("Alice", "EUR");
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("30.50"));

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("69.50"));
    }

    @Test
    void debitBeyondBalanceIsRejectedAndBalanceUnchanged() {
        Account account = new Account("Alice", "EUR");
        account.credit(new BigDecimal("10.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void zeroNegativeAndOverPreciseAmountsAreRejected() {
        Account account = new Account("Alice", "EUR");

        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-5.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("1.001"))).isInstanceOf(IllegalArgumentException.class);
    }
}
