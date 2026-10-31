package com.paynest.transfer.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletDirectoryRepository extends JpaRepository<WalletDirectoryEntry, UUID> {

    Optional<WalletDirectoryEntry> findByUserId(String userId);
}
