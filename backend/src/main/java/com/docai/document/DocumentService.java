package com.docai.document;

import com.docai.auth.AuthRepository;
import com.docai.auth.User;
import com.docai.shared.BadRequestException;
import com.docai.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AuthRepository authRepository;
    private final StorageService storageService;
    private final DocumentProcessingService documentProcessingService;

    public List<DocumentDto> getDocuments(String userEmail) {
        return documentRepository.findAllByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public DocumentDto getDocument(UUID id, String userEmail) {
        User user = authRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        return mapToDto(document);
    }

    @Transactional
    public DocumentDto uploadDocument(MultipartFile file, String userEmail) {
        User user = authRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        // Validate file size limit: 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("File size exceeds maximum limit of 10MB");
        }

        // Validate format (PDF or plain text TXT)
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".pdf") && !filename.toLowerCase().endsWith(".txt"))) {
            throw new BadRequestException("Only PDF and TXT files are supported");
        }

        // Save file to storage
        String fileKey = storageService.store(file);

        // Create document record
        Document document = new Document();
        document.setUser(user);
        document.setFilename(filename);
        document.setFileKey(fileKey);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        document.setStatus("PENDING");

        Document savedDoc = documentRepository.save(document);

        // Trigger background processing via a separate bean (cross-bean call enables @Async proxy)
        documentProcessingService.processDocumentAsync(savedDoc.getId());

        return mapToDto(savedDoc);
    }

    @Transactional
    public void deleteDocument(UUID id, String userEmail) {
        User user = authRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        // Delete physical file from storage
        storageService.delete(document.getFileKey());

        // Delete DB record. Database cascades will clean up chunks, conversations, and messages.
        documentRepository.delete(document);
    }

    private DocumentDto mapToDto(Document document) {
        return DocumentDto.builder()
                .id(document.getId())
                .filename(document.getFilename())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .chunkCount(document.getChunkCount())
                .status(document.getStatus())
                .errorMsg(document.getErrorMsg())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
