package com.minipay.common;

/** An Idempotency-Key was reused with a different request body. Mapped to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
