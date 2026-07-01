package com.docai.document;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Stores the uploaded file and returns a unique file key.
     */
    String store(MultipartFile file);

    /**
     * Deletes the file matching the provided key.
     */
    void delete(String fileKey);
}
