package com.pedro.bank.web.dto;

import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.service.PixKeyFormat;

/**
 * Who a key belongs to, for the "confirm the recipient" screen. The owner's name
 * is shown in full, as every Pix app does — a confirmation step that hides who is
 * being paid confirms nothing.
 *
 * @param own whether the key is one of the caller's own, so the app can say so
 *            instead of letting them start a transfer that will be refused
 */
public record PixRecipientResponse(String key, PixKeyType type, String display,
                                   String ownerName, String accountNumber, boolean own) {

    public static PixRecipientResponse from(PixKey key, boolean own) {
        return new PixRecipientResponse(
                key.getValue(),
                key.getType(),
                PixKeyFormat.display(key.getType(), key.getValue()),
                key.getAccount().getOwner().getName(),
                key.getAccount().getNumber(),
                own);
    }
}
