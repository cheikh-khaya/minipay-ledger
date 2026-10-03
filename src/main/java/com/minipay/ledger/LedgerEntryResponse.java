package com.minipay.ledger;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        UUID transferId,
        EntryType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getAccountId(), entry.getTransferId(),
                entry.getType(), entry.getAmount(), entry.getCreatedAt());
    }
}
