package com.leadorbit.storage;

/** Blob storage abstraction. The local adapter is the default; S3 can be added behind it. */
public interface FileStorage {

    /** Persist bytes and return a server-generated storage key. */
    String store(byte[] content, String contentType, String extension);

    /** Load a stored object by key. */
    StoredObject load(String key);
}