package com.minipay.account;

import com.minipay.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-currency account. The balance is changed only through {@link #credit} and {@link #debit},
 * which keep the money rules (positive amounts, no negative balance) in one place.
 */
@Entity
@Table(name = "accounts")
public class Account {

    /** Two decimal places, as in most currencies (EUR, USD, MAD). */
    public static final int SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = SCALE)
    private BigDecimal balance;

    /** Optimistic locking: an update fails if another transaction changed the row first. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // required by JPA
    }

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = BigDecimal.ZERO.setScale(SCALE);
        this.createdAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("Insufficient funds on account " + id);
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be strictly positive");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("Amount must have at most " + SCALE + " decimal places");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
