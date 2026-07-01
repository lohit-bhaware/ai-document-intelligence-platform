package com.docai.rag;

import com.docai.document.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;

    @Transactional
    public int saveChunks(Document document, List<RagService.PendingChunk> pendingChunks) {
        List<DocumentChunk> chunksToSave = pendingChunks.stream().map(c -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setContent(c.getText());
            chunk.setEmbedding(c.getEmbedding());
            chunk.setChunkIndex(c.getIndex());
            chunk.setPageNumber(c.getPageNumber());
            return chunk;
        }).collect(Collectors.toList());

        documentChunkRepository.saveAll(chunksToSave);
        return chunksToSave.size();
    }
}
