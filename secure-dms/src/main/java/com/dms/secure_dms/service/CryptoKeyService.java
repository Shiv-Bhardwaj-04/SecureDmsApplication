package com.dms.secure_dms.service;

import com.dms.secure_dms.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Supplies separate encryption and audit-signing keys. Environment-provided keys
 * are expected in deployed environments. The persisted local fallback makes a
 * developer demo restartable; it is deliberately called out in the README as
 * unsuitable for production key management.
 */
@Service
public class CryptoKeyService {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyService.class);
    private static final int KEY_BYTES = 32;

    private final StorageProperties storageProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey encryptionKey;
    private byte[] auditHmacKey;

    public CryptoKeyService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    void initialize() {
        Path keyDirectory = storageRoot().resolve(".keys");
        try {
            Files.createDirectories(keyDirectory);
            encryptionKey = new SecretKeySpec(
                    loadKey("DMS_ENCRYPTION_KEY", keyDirectory.resolve("document-encryption.key")), "AES");
            auditHmacKey = loadKey("DMS_AUDIT_HMAC_KEY", keyDirectory.resolve("audit-hmac.key"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize protected DMS key material", exception);
        }
    }

    public SecretKey encryptionKey() {
        return encryptionKey;
    }

    public byte[] auditHmacKey() {
        return auditHmacKey.clone();
    }

    public Path storageRoot() {
        return Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
    }

    private byte[] loadKey(String environmentVariable, Path localFile) throws IOException {
        String configuredValue = System.getenv(environmentVariable);
        if (configuredValue != null && !configuredValue.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configuredValue.trim());
                validateKeyLength(decoded, environmentVariable);
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(environmentVariable + " must be a Base64-encoded 32-byte key", exception);
            }
        }

        if (Files.exists(localFile)) {
            byte[] existing = Files.readAllBytes(localFile);
            validateKeyLength(existing, localFile.toString());
            return existing;
        }

        byte[] generated = new byte[KEY_BYTES];
        secureRandom.nextBytes(generated);
        try {
            Files.write(localFile, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            return Files.readAllBytes(localFile);
        }
        log.warn("{} is not configured; generated a local development key at {}. Use a managed secret in production.",
                environmentVariable, localFile);
        return generated;
    }

    private void validateKeyLength(byte[] key, String source) {
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(source + " must contain exactly " + KEY_BYTES + " bytes");
        }
    }
}
