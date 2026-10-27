package com.dms.secure_dms.service;

import java.io.InputStream;

public record DownloadableDocument(String originalFilename, String contentType, long byteSize, InputStream inputStream) {
}
