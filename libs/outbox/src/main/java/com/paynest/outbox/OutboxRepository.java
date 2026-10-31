package com.paynest.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished rows. {@code SKIP LOCKED} lets several relay instances run at
     * the same time without picking the same rows (T-OUT-4).
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByTopicOrderByCreatedAt(String topic);
}
