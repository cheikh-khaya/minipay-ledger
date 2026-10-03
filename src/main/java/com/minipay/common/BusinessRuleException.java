package com.minipay.common;

/**
 * The request is well-formed but breaks a business rule
 * (insufficient funds, currency mismatch, transfer to the same account).
 * Mapped to HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
