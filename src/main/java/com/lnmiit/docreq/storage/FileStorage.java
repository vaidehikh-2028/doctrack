package com.lnmiit.docreq.storage;

/**
 * Object storage abstraction. Production uses S3 ({@link S3FileStorage});
 * local development and tests use the filesystem ({@link LocalFileStorage}).
 */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
