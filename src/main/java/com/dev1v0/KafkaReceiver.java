package com.dev1v0;

import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class KafkaReceiver {

    private static final Logger log = Logger.getLogger(KafkaReceiver.class);

    @Incoming("main-in")
    public CompletionStage<Void> consume(Message<SampleData> message) {
        var metadata = message.getMetadata(IncomingKafkaRecordMetadata.class).orElseThrow();
        log.infof("Received message. Id: %s; Data: %s", metadata.getKey(), message.getPayload());
        return message.ack();
    }

}
