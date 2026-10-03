package com.minipay.ledger;

public enum EntryType {
    /** Money coming from outside the system (simulated funding). */
    DEPOSIT,
    /** Money leaving an account as part of a transfer. */
    DEBIT,
    /** Money arriving on an account as part of a transfer. */
    CREDIT
}
