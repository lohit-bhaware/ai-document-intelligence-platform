package com.docai.document;

import com.docai.rag.ParsingService;
import com.docai.rag.RagService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final ParsingService parsingService;
    private final RagService ragService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Async("documentTaskExecutor")
    public void processDocumentAsync(UUID documentId) {
        try {
            updateStatus(documentId, "PROCESSING", null, 0);

            Document document = documentRepository.findById(documentId).orElse(null);
            if (document == null) {
                return;
            }

            Path filePath = Paths.get(uploadDir).resolve(document.getFileKey()).normalize().toAbsolutePath();

            // Step 1: Parse file (delegated to rag.ParsingService)
            List<RagService.ParsedPage> pages = parsingService.parse(filePath, document.getFilename());

            // Steps 2-4: Chunk, embed, and persist (delegated to rag.RagService)
            int chunkCount = ragService.ingestDocument(document, pages);

            // Update status to READY
            updateStatus(documentId, "READY", null, chunkCount);

        } catch (Exception e) {
            log.error("Document processing failed for documentId={}", documentId, e);
            try {
                updateStatus(documentId, "FAILED", e.getMessage() != null ? e.getMessage() : e.toString(), 0);
            } catch (Exception ex) {
                log.error("Failed to update status for documentId={}", documentId, ex);
            }
        }
    }

    @Transactional
    public void updateStatus(UUID documentId, String status, String errorMsg, int chunkCount) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setErrorMsg(errorMsg);
            doc.setChunkCount(chunkCount);
            documentRepository.save(doc);
        });
    }
}
