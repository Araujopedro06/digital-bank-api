package com.pedro.bank.domain;

/**
 * How a transfer was addressed. The money moves identically either way — the
 * rail only decides how the two ledger lines are labelled, so the statement can
 * say "Pix enviado" rather than "Transferência enviada".
 */
public enum TransferRail {

    ACCOUNT_NUMBER(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN),
    PIX(TransactionType.PIX_OUT, TransactionType.PIX_IN);

    private final TransactionType outgoing;
    private final TransactionType incoming;

    TransferRail(TransactionType outgoing, TransactionType incoming) {
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    public TransactionType outgoing() {
        return outgoing;
    }

    public TransactionType incoming() {
        return incoming;
    }
}
