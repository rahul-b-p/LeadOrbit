package com.leadorbit.storage;

/** Loaded blob plus its resolved content type. */
public record StoredObject(byte[] content, String contentType) {
}