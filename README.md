This is a sample project on how to send [CloudEvents](https://cloudevents.io/) with [Quarkus](https://quarkus.io/) and [Apache Kafka](https://kafka.apache.org/)


In Contrast with Spring, Quarkus has excellent in-build support for Kafka and Cloud Events so the configuration is minimal

# Initial Configuration

## Running Kafka

The simplest way to run Kafka is using Docker containers and Docker Compose. Here is my compose file using [bitnami](https://bitnami.com/) images:
```yaml
version: "3"  
services:  
  zookeeper:  
    image: 'bitnami/zookeeper:latest'  
    container_name: zookeeper  
    ports:  
      - '2181:2181'  
    environment:  
      - ALLOW_ANONYMOUS_LOGIN=yes  
  kafka:  
    image: 'bitnami/kafka:latest'  
    container_name: kafka  
    ports:  
      - '9092:9092'  
    environment:  
      - KAFKA_BROKER_ID=1  
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092  
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:9092  
      - KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181  
      - ALLOW_PLAINTEXT_LISTENER=yes  
    depends_on:  
      - zookeeper
```

## Dependancies

Quarkus relies on [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/4.6.0/) to handle Kafka integrations. So we need to add that extension. Using Quarkus CLI:

```bash
quarkus extension add 'smallrye-reactive-messaging-kafka'
```

## Configuration

In order to configure the Kafka integration we need to add few things to application.properties.
First we configure the connection and enable cloud events:

```properties
kafka.bootstrap.servers=localhost:9092
kafka.cloud-events=true
```

In reality cloud events are enabled by default, but I like to add explicit configuration to make that obvious.

Then we configure our outgoing connector, topic and some event parameters:

```properties
mp.messaging.outgoing.main-out.connector=smallrye-kafka
mp.messaging.outgoing.main-out.topic=main-topic
mp.messaging.outgoing.main-out.cloud-events-source=https://1v0dev/producer
mp.messaging.outgoing.main-out.cloud-events-type=com.dev1v0.producer
```

In the given configuration, `main-out` represents the name of our outgoing connector. You can replace it with any suitable name according to your requirements. In practice, only the first line is necessary. If you do not provide a topic name, the connector name will be used as the default topic. The last two lines define event properties, which can be omitted if they are not needed.

We configure the incomming connector the same way:
```properties
mp.messaging.incoming.main-in.connector=smallrye-kafka
mp.messaging.incoming.main-in.topic=main-topic
```

# Producing events

Now we are ready to create our producer:
```java
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
```

There are several important bits here:

`@Outgoing("main-out")` this annotation specifies the name of the connector, it's the same as we defined it in the application.properties.

`public Multi<Message<SampleData>>` we wrap our data into a Message which allows us to add some metadata, like the event key.

Quarkus does not automatically call our method periodically the same way [Spring Cloud Stream does](https://1v0.dev/posts/19-spring-stream-kafka-cloudevents/#producing-events). So instead we configure the producer itself by using ticks and 1 second interval `Multi.createFrom().ticks().every(Duration.ofSeconds(1))`

I show the imports in the code sample above because there were some duplicate names even in this minimal quarkus project.

# Receiving Events

Receiving messages is very simple. I will omit most of the boilerplate code this time and will show only the method

```java
@Incoming("main-in")
public CompletionStage<Void> consume(Message<SampleData> message) {
        var metadata = message.getMetadata(IncomingKafkaRecordMetadata.class).orElseThrow();
        log.infof("Received message. Id: %s; Data: %s", metadata.getKey(), message.getPayload());
        return message.ack();
        }
```
Again we specify the connector using an annotation `@Incoming("main-in")`.

We receive `Message<SampleData> message` in order to be able to read the event metadata. If you don't need that, the method can receive `SampleData` directly.

Also in case we use `Message` wrapper, we need to acknowledge that we receive the message manually `return message.ack()`

# Conclusion

And that is it. If you have Kafka running, you can start the sample app and you will send and receive CloudEvent messages through Kafka.

Here is a part of the log:

```log
2023-05-29 10:41:57,721 INFO  [com.dev.KafkaReceiver] (vert.x-eventloop-thread-3) Received message. Id: 70e1bfa4-0ebb-45fe-afb4-2eeaa1229fd8; Data: SampleData{name='Johnny Roob', age=60, company='Cummings-Cassin'}
2023-05-29 10:41:57,726 INFO  [com.dev.KafkaReceiver] (vert.x-eventloop-thread-3) Received message. Id: 5c864131-d982-49cb-9abe-1e9f2e09f03d; Data: SampleData{name='Shondra Wolff', age=47, company='Rath-Schroeder'}
2023-05-29 10:41:57,727 INFO  [com.dev.KafkaReceiver] (vert.x-eventloop-thread-3) Received message. Id: f25d4846-96ce-4828-9e42-345b78f844c3; Data: SampleData{name='Albert Yundt Sr.', age=26, company='Schuster and Sons'}
2023-05-29 10:41:58,717 INFO  [com.dev.KafkaReceiver] (vert.x-eventloop-thread-3) Received message. Id: 8ea1f484-d11e-4467-a108-659f8374a81a; Data: SampleData{name='Shantay Hane', age=68, company='Hand Inc'}

```