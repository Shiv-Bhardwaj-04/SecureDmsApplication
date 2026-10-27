package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.DocumentVersion;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stores document bytes as AES-256-GCM encrypted blobs, never under their original file names. */
@Service
public class EncryptedFileStorageService {

    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "csv", "doc", "docx", "png", "jpg", "jpeg"
    );

    private final CryptoKeyService cryptoKeyService;
    private final SecureRandom secureRandom = new SecureRandom();
    private Path blobDirectory;

    public EncryptedFileStorageService(CryptoKeyService cryptoKeyService) {
        this.cryptoKeyService = cryptoKeyService;
    }

    @PostConstruct
    void initialize() {
        blobDirectory = cryptoKeyService.storageRoot().resolve("blobs").normalize();
        try {
            Files.createDirectories(blobDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create encrypted document storage", exception);
        }
    }

    public StoredFile store(MultipartFile upload) {
        String originalFilename = validateAndCleanFilename(upload);
        String contentType = safeContentType(upload.getContentType());
        String storageKey = UUID.randomUUID() + ".bin";
        Path destination = resolveStoragePath(storageKey);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        long byteSize = 0;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            try (InputStream source = upload.getInputStream();
                 OutputStream encryptedFile = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE);
                 CipherOutputStream encryptedOutput = new CipherOutputStream(encryptedFile, cipher)) {
                byte[] buffer = new byte[16 * 1024];
                int bytesRead;
                while ((bytesRead = source.read(buffer)) != -1) {
                    byteSize += bytesRead;
                    if (byteSize > MAX_FILE_BYTES) {
                        throw new IllegalArgumentException("A document may not exceed 25 MB");
                    }
                    digest.update(buffer, 0, bytesRead);
                    encryptedOutput.write(buffer, 0, bytesRead);
                }
            }
            return new StoredFile(storageKey, Base64.getEncoder().encodeToString(iv), originalFilename,
                    contentType, byteSize, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            discard(storageKey);
            throw new IllegalStateException("The encrypted document could not be stored", exception);
        } catch (RuntimeException exception) {
            discard(storageKey);
            throw exception;
        }
    }

    public InputStream open(DocumentVersion version) {
        try {
            byte[] iv = Base64.getDecoder().decode(version.getEncryptionIv());
            return new CipherInputStream(Files.newInputStream(resolveStoragePath(version.getStorageKey())),
                    cipher(Cipher.DECRYPT_MODE, iv));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("The encrypted document blob is unavailable or invalid", exception);
        }
    }

    /** Only used to clean up a blob created during a failed database transaction. */
    public void discard(String storageKey) {
        try {
            Files.deleteIfExists(resolveStoragePath(storageKey));
        } catch (IOException ignored) {
            // An orphaned encrypted blob is safer than hiding the original request failure.
        }
    }

    private String validateAndCleanFilename(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty document to upload");
        }
        if (upload.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("A document may not exceed 25 MB");
        }

        String rawFilename = upload.getOriginalFilename();
        String filename = StringUtils.cleanPath(rawFilename == null ? "" : rawFilename)
                .replace('\\', '/');
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("The document file name is invalid");
        }
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.length() > 255) {
            throw new IllegalArgumentException("The document file name is too long");
        }

        int dot = filename.lastIndexOf('.');
        String extension = dot < 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF, Office, text, CSV, PNG, and JPEG documents are accepted");
        }
        return filename;
    }

    private String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > 120) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private Cipher cipher(int mode, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, cryptoKeyService.encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize document encryption", exception);
        }
    }

    private Path resolveStoragePath(String storageKey) {
        if (!storageKey.matches("[0-9a-f-]{36}\\.bin")) {
            throw new IllegalArgumentException("Invalid encrypted storage key");
        }
        Path resolved = blobDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(blobDirectory)) {
            throw new IllegalArgumentException("Invalid encrypted storage path");
        }
        return resolved;
    }
}
