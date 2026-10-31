package com.paynest.ledger.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;

@Component
public class LedgerListener {

    private final LedgerMessageService messages;
    private final EventCodec codec;

    public LedgerListener(LedgerMessageService messages, EventCodec codec) {
        this.messages = messages;
        this.codec = codec;
    }

    @KafkaListener(topics = {Topics.LEDGER_COMMANDS, Topics.WALLET_EVENTS}, groupId = "ledger-service")
    public void onMessage(String message) {
        messages.handle(codec.decode(message));
    }
}
