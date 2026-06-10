/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.messaging;

import com.mehmandarov.jhg.domain.ConfEvent;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that the reactive-messaging path works against a <em>real</em>
 * Kafka broker: boots a Redpanda container, publishes a {@link ConfEvent} to
 * {@code confs.events.incoming}, consumes it back, and feeds it through the
 * production {@link EventConsumer} to confirm parse + dedupe.
 *
 * <p>Skips automatically when Docker isn't available.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedpandaRoundTripIT {

    @Container
    static final RedpandaContainer REDPANDA =
            new RedpandaContainer(DockerImageName.parse("redpandadata/redpanda:v24.2.4"));

    private final Jsonb jsonb = JsonbBuilder.create();

    private ConfEvent sample(String id) {
        return new ConfEvent(id, "JFokus", "Stockholm",
                LocalDate.of(2027, 2, 1), LocalDate.of(2026, 9, 1),
                Set.of("java", "cloud-native"), Set.of("en"),
                "https://jfokus.se", "https://jfokus.se/cfp",
                Set.of("conf"), Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void eventRoundTripsThroughRedpanda() throws Exception {
        String bootstrap = REDPANDA.getBootstrapServers();
        ConfEvent event = sample("jfokus-2027");
        String json = jsonb.toJson(event);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps(bootstrap))) {
            producer.send(new ProducerRecord<>(EventChannels.EVENTS_TOPIC, event.id(), json)).get();
        }

        String received = null;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(bootstrap))) {
            consumer.subscribe(List.of(EventChannels.EVENTS_TOPIC));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
            outer:
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> rec : records) {
                    received = rec.value();
                    break outer;
                }
            }
        }

        assertThat(received).as("a record came back off the topic").isNotNull();
        assertThat(received).isEqualTo(json);

        // Feed it through the production consumer: fresh first, deduped on replay.
        EventConsumer ec = new EventConsumer();
        assertThat(ec.consume(received)).isTrue();
        assertThat(ec.consume(received)).isFalse();
    }

    private static Properties producerProps(String bootstrap) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        return p;
    }

    private static Properties consumerProps(String bootstrap) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "confspeakerhub-it");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return p;
    }
}

