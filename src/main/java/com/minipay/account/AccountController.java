package com.minipay.account;

import com.minipay.account.AccountDtos.AccountResponse;
import com.minipay.account.AccountDtos.CreateAccountRequest;
import com.minipay.account.AccountDtos.DepositRequest;
import com.minipay.ledger.LedgerEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Open an account with a zero balance")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = service.create(request.ownerName(), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account and its current balance")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(service.get(id));
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Add funds to an account (simulated external deposit)")
    public AccountResponse deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(service.deposit(id, request.amount()));
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "List the ledger entries of an account, oldest first")
    public List<LedgerEntryResponse> entries(@PathVariable UUID id) {
        return service.entries(id).stream().map(LedgerEntryResponse::from).toList();
    }
}
