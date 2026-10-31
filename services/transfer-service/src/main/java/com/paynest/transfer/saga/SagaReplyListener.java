package com.paynest.transfer.saga;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;
import com.paynest.transfer.service.TransferSaga;

/** Replies from wallet-service and ledger-service drive the saga forward. */
@Component
public class SagaReplyListener {

    private final TransferSaga saga;
    private final EventCodec codec;

    public SagaReplyListener(TransferSaga saga, EventCodec codec) {
        this.saga = saga;
        this.codec = codec;
    }

    @KafkaListener(topics = {Topics.WALLET_EVENTS, Topics.LEDGER_EVENTS}, groupId = "transfer-service")
    public void onMessage(String message) {
        saga.handle(codec.decode(message));
    }
}
