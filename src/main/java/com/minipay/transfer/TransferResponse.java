package com.minipay.transfer;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        Instant createdAt) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(),
                transfer.getAmount(), transfer.getCurrency(), transfer.getCreatedAt());
    }
}
