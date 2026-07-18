package com.platolisto.restaurant_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Cliente S3 SDK v2 apuntando a Cloudflare R2.
 * Activo solo con {@code cloudflare.r2.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "cloudflare.r2", name = "enabled", havingValue = "true")
public class R2ClientConfig {

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(
            @Value("${cloudflare.r2.endpoint}") String endpoint,
            @Value("${cloudflare.r2.access-key}") String accessKey,
            @Value("${cloudflare.r2.secret-key}") String secretKey
    ) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                // R2 ignora la región; el SDK v2 la exige.
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }
}
