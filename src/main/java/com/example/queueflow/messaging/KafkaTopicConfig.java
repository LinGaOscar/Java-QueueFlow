package com.example.queueflow.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic queueCreatedTopic(@Value("${app.kafka.topic.queue-created}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic queueAdmittedTopic(@Value("${app.kafka.topic.queue-admitted}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic queueCancelledTopic(@Value("${app.kafka.topic.queue-cancelled}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic queueExpiredTopic(@Value("${app.kafka.topic.queue-expired}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
