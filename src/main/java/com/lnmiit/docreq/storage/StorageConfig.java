package com.lnmiit.docreq.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorage localFileStorage(@Value("${app.storage.local-root:./data/files}") String root) {
        return new LocalFileStorage(Path.of(root));
    }

    /** Credentials come from the default AWS chain (an IAM role when running on EC2). */
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStorage s3FileStorage(S3Client s3Client, @Value("${app.storage.s3.bucket}") String bucket) {
        return new S3FileStorage(s3Client, bucket);
    }
}
