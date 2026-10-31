package com.paynest.wallet.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findTop50ByWalletIdOrderByCreatedAtDesc(UUID walletId);

    boolean existsByWalletIdAndTypeAndReference(UUID walletId, TransactionType type, String reference);
}
