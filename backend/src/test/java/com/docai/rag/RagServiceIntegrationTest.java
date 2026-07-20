package com.docai.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RagServiceIntegrationTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void testEmbeddingChunkingBatch() {
        // Mock the embedding model behavior for a single chunk
        org.springframework.ai.embedding.Embedding mockEmbedding = new org.springframework.ai.embedding.Embedding(List.of(0.1, 0.2, 0.3), 0);
        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(mockEmbedding));
        
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(mockResponse);

        List<float[]> embeddings = embeddingService.embedChunks(List.of("Chunk 1"));

        assertNotNull(embeddings);
        assertEquals(1, embeddings.size());
        assertEquals(3, embeddings.get(0).length);
        assertEquals(0.1f, embeddings.get(0)[0], 0.001);
    }
}
