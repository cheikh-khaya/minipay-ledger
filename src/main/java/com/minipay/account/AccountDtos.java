package com.minipay.account;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies for the account endpoints. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR or MAD")
            String currency) {
    }

    public record DepositRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    /** Money is serialized as a string ("70.00") so clients never parse it as a floating-point number. */
    public record AccountResponse(
            UUID id,
            String ownerName,
            String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
            Instant createdAt) {

        static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                    account.getBalance(), account.getCreatedAt());
        }
    }
}
