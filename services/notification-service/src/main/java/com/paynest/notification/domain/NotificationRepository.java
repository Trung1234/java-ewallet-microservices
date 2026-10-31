package com.paynest.notification.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(String userId, Channel channel, Pageable page);

    long countByUserIdAndChannelAndReadFalse(String userId, Channel channel);

    List<Notification> findByTransferId(String transferId);
}
