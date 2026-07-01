package com.docai.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    
    @Query(value = "SELECT * FROM document_chunks WHERE document_id = :documentId " +
            "ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :limit", 
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(
            @Param("documentId") UUID documentId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit
    );
}
