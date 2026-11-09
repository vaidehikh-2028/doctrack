package com.lnmiit.docreq.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Stores files in a private S3 bucket. Downloads go through the API (not public
 * URLs) so that BR-009 (only the requesting student may download) is always checked.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
