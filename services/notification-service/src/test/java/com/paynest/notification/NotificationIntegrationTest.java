package com.paynest.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.paynest.events.EventCodec;
import com.paynest.events.EventEnvelope;
import com.paynest.events.Message;
import com.paynest.events.TransferMessages.TransferCompleted;
import com.paynest.events.TransferMessages.TransferFailed;
import com.paynest.notification.domain.Channel;
import com.paynest.notification.domain.Notification;
import com.paynest.notification.domain.NotificationRepository;
import com.paynest.notification.service.NotificationHandler;
import com.paynest.notification.service.PreferenceView;

/** T-NTF-1, T-NTF-2, T-NTF-3, T-NTF-5, T-NTF-6. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class NotificationIntegrationTest {

    @Autowired
    NotificationHandler handler;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    EventCodec codec;

    @Autowired
    MockMvc mvc;

    @Autowired
    CacheManager cacheManager;

    String alice = "alice-" + UUID.randomUUID();
    String bob = "bob-" + UUID.randomUUID();

    @Test
    void completedTransferNotifiesBothParties() { // T-NTF-1
        UUID transferId = UUID.randomUUID();

        handler.handle(envelope(completed(transferId, 150_000)));

        List<Notification> sent = notifications.findByTransferId(transferId.toString());
        assertThat(sent).extracting(Notification::getUserId, Notification::getChannel).containsExactlyInAnyOrder(
                tuple(alice, Channel.IN_APP), tuple(alice, Channel.EMAIL),
                tuple(bob, Channel.IN_APP), tuple(bob, Channel.EMAIL));
        assertThat(sent).allMatch(n -> n.getStatus() == Notification.Status.SENT);
        assertThat(sent).filteredOn(n -> n.getUserId().equals(bob)).first()
                .extracting(Notification::getBody).asString().contains("150.000 VND");
    }

    @Test
    void failedTransferNotifiesSender() { // T-NTF-2
        UUID transferId = UUID.randomUUID();

        handler.handle(envelope(new TransferFailed(transferId, UUID.randomUUID(), alice, UUID.randomUUID(), bob, 900, "INSUFFICIENT_FUNDS")));

        assertThat(notifications.findByTransferId(transferId.toString()))
                .isNotEmpty()
                .allMatch(n -> n.getUserId().equals(alice));
    }

    @Test
    void sameEventTwiceNotifiesOnce() { // T-NTF-3, INV-6
        UUID transferId = UUID.randomUUID();
        EventEnvelope event = envelope(completed(transferId, 1_000));

        handler.handle(event);
        handler.handle(event);

        assertThat(notifications.findByTransferId(transferId.toString())).hasSize(4);
    }

    @Test
    void inboxListReadAndUnreadCount() throws Exception { // T-NTF-5
        handler.handle(envelope(completed(UUID.randomUUID(), 1_000)));
        handler.handle(envelope(completed(UUID.randomUUID(), 2_000)));

        mvc.perform(get("/notifications/unread-count").with(jwt().jwt(j -> j.subject(bob))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        String list = mvc.perform(get("/notifications").with(jwt().jwt(j -> j.subject(bob))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(list, "$[0].id");

        mvc.perform(patch("/notifications/" + firstId + "/read").with(jwt().jwt(j -> j.subject(alice))))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/notifications/" + firstId + "/read").with(jwt().jwt(j -> j.subject(bob))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/notifications/unread-count").with(jwt().jwt(j -> j.subject(bob))))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void preferencesAreRespectedAndCached() throws Exception { // T-NTF-6
        mvc.perform(put("/preferences").with(jwt().jwt(j -> j.subject(bob)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailEnabled\":false,\"pushEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));
        assertThat(cacheManager.getCache("preferences").get(bob, PreferenceView.class))
                .isEqualTo(new PreferenceView(bob, false, true));

        UUID transferId = UUID.randomUUID();
        handler.handle(envelope(completed(transferId, 1_000)));

        assertThat(notifications.findByTransferId(transferId.toString()))
                .filteredOn(n -> n.getUserId().equals(bob))
                .extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(Channel.IN_APP, Channel.PUSH);
    }

    private TransferCompleted completed(UUID transferId, long amount) {
        return new TransferCompleted(transferId, UUID.randomUUID(), alice, UUID.randomUUID(), bob, amount);
    }

    private EventEnvelope envelope(Message message) {
        return codec.decode(codec.encode(UUID.randomUUID(), message));
    }
}
