package com.minipay.transfer;

import com.minipay.transfer.TransferService.TransferResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Transfer money between two accounts",
            description = "201 for a new transfer, 200 when the same Idempotency-Key and payload are replayed.")
    public ResponseEntity<TransferResponse> create(
            @Parameter(description = "Unique key chosen by the client, e.g. a UUID. Reuse it when retrying.")
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        TransferResult result = service.transfer(idempotencyKey, request);
        TransferResponse body = TransferResponse.from(result.transfer());

        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transfer")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(service.get(id));
    }
}
