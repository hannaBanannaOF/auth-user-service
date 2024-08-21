package com.hbsites.auth.infrastructure.listener;

import com.hbsites.auth.application.service.KeycloakService;
import com.hbsites.commons.infrastructure.messages.incoming.UserInfoRequestMessage;
import com.hbsites.commons.infrastructure.messages.outgoing.UserInfoResponseMessage;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class UserServiceListener {

    @Inject
    private KeycloakService service;

    @Inject
    @Channel("user-response-exchange")
    Emitter<UserInfoResponseMessage> emitter;

    @Incoming("user-request")
    @Blocking
    public Uni<Void> process(Message<JsonObject> jsonMessage) {
        UserInfoRequestMessage message = jsonMessage.getPayload().mapTo(UserInfoRequestMessage.class);
        return service.getUsersById(message).onItem().transform(userInfoResponseMessage -> {
            // Get headers from previous message
            AtomicReference<Map<String, Object>> headers = new AtomicReference<>(new HashMap<>());
            jsonMessage.getMetadata(IncomingRabbitMQMetadata.class).ifPresent(incomingRabbitMQMetadata -> {
                headers.set(incomingRabbitMQMetadata.getHeaders());
            });

            // Build outgoing metadata
            OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                    .withRoutingKey(message.getMicroservice().getUserResponseRoutingKey())
                    .withHeaders(headers.get())
                    .build();

            // Ack message
            jsonMessage.ack();

            // Send back user info
            emitter.send(Message.of(userInfoResponseMessage, Metadata.of(metadata)));
            return null;
        }).onFailure().invoke(jsonMessage::nack).replaceWithVoid();
    }

}
