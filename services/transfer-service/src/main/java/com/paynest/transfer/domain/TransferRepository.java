package com.paynest.transfer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByFromUserIdAndIdempotencyKey(String fromUserId, String idempotencyKey);

    List<Transfer> findTop50ByFromUserIdOrToUserIdOrderByCreatedAtDesc(String fromUserId, String toUserId);
}
