package com.minipay.transfer;

import com.minipay.account.Account;
import com.minipay.account.AccountRepository;
import com.minipay.common.BusinessRuleException;
import com.minipay.common.IdempotencyConflictException;
import com.minipay.common.NotFoundException;
import com.minipay.ledger.EntryType;
import com.minipay.ledger.LedgerEntry;
import com.minipay.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public TransferService(TransferRepository transfers, AccountRepository accounts, LedgerEntryRepository ledger) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** Result of a transfer call: the transfer and whether it was replayed from a previous request. */
    public record TransferResult(Transfer transfer, boolean replayed) {
    }

    /**
     * Moves money between two accounts in one database transaction.
     * <ul>
     *   <li>Same Idempotency-Key + same payload: returns the original transfer, no money moves again.</li>
     *   <li>Same Idempotency-Key + different payload: rejected with 409.</li>
     * </ul>
     * Either everything is saved (both balances, the transfer, two ledger entries) or nothing is.
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, TransferRequest request) {
        validateKey(idempotencyKey);

        Optional<Transfer> previous = transfers.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().matches(request)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key '" + idempotencyKey + "' was already used with a different request");
            }
            return new TransferResult(previous.get(), true);
        }

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Source and destination accounts must be different");
        }
        Account from = findAccount(request.fromAccountId());
        Account to = findAccount(request.toAccountId());
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Currency mismatch: " + from.getCurrency() + " -> " + to.getCurrency());
        }

        from.debit(request.amount());   // throws 422 on insufficient funds, before anything is written
        to.credit(request.amount());

        Transfer transfer = transfers.save(new Transfer(idempotencyKey, from.getId(), to.getId(),
                request.amount(), from.getCurrency()));
        ledger.save(new LedgerEntry(from.getId(), transfer.getId(), EntryType.DEBIT, request.amount()));
        ledger.save(new LedgerEntry(to.getId(), transfer.getId(), EntryType.CREDIT, request.amount()));
        return new TransferResult(transfer, false);
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer " + id + " not found"));
    }

    private Account findAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 1 to " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }
}
