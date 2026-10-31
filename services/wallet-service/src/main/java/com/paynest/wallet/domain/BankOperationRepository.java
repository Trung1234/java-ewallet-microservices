package com.paynest.wallet.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankOperationRepository extends JpaRepository<BankOperation, UUID> {

    Optional<BankOperation> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);
}
