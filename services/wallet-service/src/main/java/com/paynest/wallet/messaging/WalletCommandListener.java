package com.paynest.wallet.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;

@Component
public class WalletCommandListener {

    private final WalletCommandService commands;
    private final EventCodec codec;

    public WalletCommandListener(WalletCommandService commands, EventCodec codec) {
        this.commands = commands;
        this.codec = codec;
    }

    /** Keyed by wallet id: commands for one wallet are processed one after another. */
    @KafkaListener(topics = Topics.WALLET_COMMANDS, groupId = "wallet-service")
    public void onMessage(String message) {
        commands.handle(codec.decode(message));
    }
}
