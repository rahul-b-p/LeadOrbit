package com.leadorbit.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.leadorbit.config.StorageProperties;

/**
 * Local filesystem adapter. Keys are server-generated (never client-supplied) so path
 * traversal is structurally impossible; the public controller streams via this service.
 */
@Service
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = Paths.get(properties.basePath()).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String contentType, String extension) {
        String safeExtension = extension == null ? "" : extension.replaceAll("[^a-zA-Z0-9]", "");
        String key = "payments/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID()
            + (safeExtension.isEmpty() ? "" : "." + safeExtension.toLowerCase());
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist file: " + key, ex);
        }
        return key;
    }

    @Override
    public StoredObject load(String key) {
        Path target = resolve(key);
        try {
            return new StoredObject(Files.readAllBytes(target), Files.probeContentType(target));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Stored file not found: " + key, ex);
        }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}