package com.minipay.account;

import com.minipay.common.NotFoundException;
import com.minipay.ledger.EntryType;
import com.minipay.ledger.LedgerEntry;
import com.minipay.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account create(String ownerName, String currencyCode) {
        Currency.getInstance(currencyCode); // throws IllegalArgumentException for unknown codes -> 400
        return accounts.save(new Account(ownerName.trim(), currencyCode));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    /** Simulates money arriving from outside the system, so accounts can be funded in a demo. */
    @Transactional
    public Account deposit(UUID accountId, BigDecimal amount) {
        Account account = get(accountId);
        account.credit(amount);
        ledger.save(new LedgerEntry(account.getId(), null, EntryType.DEPOSIT, amount));
        return account;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entries(UUID accountId) {
        get(accountId); // 404 if the account does not exist
        return ledger.findByAccountIdOrderByCreatedAtAsc(accountId);
    }
}
