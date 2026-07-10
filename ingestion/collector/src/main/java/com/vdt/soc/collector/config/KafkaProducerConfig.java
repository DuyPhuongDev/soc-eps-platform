package com.vdt.soc.collector.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:PLAIN}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String truststoreLocation;

    @Value("${spring.kafka.properties.ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${spring.kafka.producer.acks:1}")
    private String acks;

    @Value("${spring.kafka.producer.compression-type:lz4}")
    private String compressionType;

    @Value("${spring.kafka.producer.linger-ms:5}")
    private int lingerMs;

    @Value("${spring.kafka.producer.batch-size:16384}")
    private int batchSize;

    @Value("${spring.kafka.producer.retries:0}")
    private int retries;

    @Value("${spring.kafka.producer.max-in-flight-requests-per-connection:5}")
    private int maxInFlight;

    @Bean
    public KafkaSender<String, String> kafkaSender() {

        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlight);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        // Security
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        if (!saslMechanism.isBlank()) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }

        if (!saslJaasConfig.isBlank()) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        }

        if (!truststoreLocation.isBlank()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
        }

        if (!truststorePassword.isBlank()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
        }

        return KafkaSender.create(SenderOptions.create(props));
    }
}