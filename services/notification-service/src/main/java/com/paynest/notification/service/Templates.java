package com.paynest.notification.service;

import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.events.TransferMessages.TransferFailed;

/** Message texts (Vietnamese). Amounts are minor units of VND, i.e. whole dong. */
@Component
public class Templates {

    public record Content(String title, String body) {
    }

    public Content transferSent(TransferCompleted event) {
        return new Content("Chuyển tiền thành công",
                "Bạn đã chuyển " + money(event.amount()) + " tới ví " + event.toWalletId() + ".");
    }

    public Content transferReceived(TransferCompleted event) {
        return new Content("Bạn nhận được tiền",
                "Bạn đã nhận " + money(event.amount()) + " từ ví " + event.fromWalletId() + ".");
    }

    public Content transferFailed(TransferFailed event) {
        return new Content("Chuyển tiền không thành công",
                "Giao dịch " + money(event.amount()) + " tới ví " + event.toWalletId()
                        + " không thành công (" + event.reason() + "). Số dư của bạn không bị trừ.");
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " VND";
    }
}
