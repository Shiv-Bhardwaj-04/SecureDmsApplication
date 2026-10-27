package com.dms.secure_dms.service;

/** Metadata calculated while a file is encrypted and written to local storage. */
public record StoredFile(
        String storageKey,
        String encryptionIv,
        String originalFilename,
        String contentType,
        long byteSize,
        String sha256
) {
}
