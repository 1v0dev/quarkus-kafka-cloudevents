package com.dev1v0;

import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

@ApplicationScoped
public class KafkaSender {

    private final Random random = new Random();

    @Outgoing("main-out")
    public Multi<Message<SampleData>> generate() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .map(x -> Message.of(new SampleData())
                        .addMetadata(OutgoingKafkaRecordMetadata.builder()
                                .withKey(UUID.randomUUID().toString())
                                .build()));
    }

}
