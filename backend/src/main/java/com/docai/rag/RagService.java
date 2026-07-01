package com.docai.rag;

import com.docai.document.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagService {

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RagPersistenceService ragPersistenceService;
    private final DocumentChunkRepository documentChunkRepository;

    // ────────────────────────────────────────────
    // Ingestion (upload time)
    // ────────────────────────────────────────────

    public int ingestDocument(Document document, List<ParsedPage> pages) {
        List<PendingChunk> pendingChunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ParsedPage page : pages) {
            List<String> pageChunks = chunkingService.splitIntoChunks(page.getText());
            for (String text : pageChunks) {
                pendingChunks.add(new PendingChunk(text, chunkIndex++, page.getPageNumber()));
            }
        }

        if (pendingChunks.isEmpty()) {
            return 0;
        }

        List<String> chunkTexts = pendingChunks.stream().map(PendingChunk::getText).collect(Collectors.toList());
        List<float[]> embeddings = embeddingService.embedChunks(chunkTexts);

        for (int i = 0; i < pendingChunks.size(); i++) {
            pendingChunks.get(i).setEmbedding(embeddings.get(i));
        }

        return ragPersistenceService.saveChunks(document, pendingChunks);
    }

    // ────────────────────────────────────────────
    // Query (chat time)
    // ────────────────────────────────────────────

    public List<DocumentChunk> searchChunks(java.util.UUID documentId, String question, int topK) {
        float[] queryVector = embeddingService.embedQuery(question);
        String vectorString = Arrays.toString(queryVector);
        return documentChunkRepository.findSimilarChunks(documentId, vectorString, topK);
    }

    public List<Message> buildPromptMessages(
            List<DocumentChunk> chunks,
            List<HistoryEntry> recentHistory,
            String question
    ) {
        List<Message> messages = new ArrayList<>();

        // 1. System message
        messages.add(new SystemMessage(
            "You are a helpful assistant. Answer only using the context below. " +
            "If the answer is not in the context, say so. " +
            "Cite chunk numbers in your answer using [Chunk N] notation."
        ));

        // 2. Context chunks
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            contextBuilder.append("[Chunk ").append(chunk.getChunkIndex())
                    .append(", page ").append(chunk.getPageNumber()).append("]: \"")
                    .append(chunk.getContent()).append("\"\n\n");
        }
        messages.add(new UserMessage("Context:\n" + contextBuilder));

        // 3. Last N messages of conversation history
        for (HistoryEntry entry : recentHistory) {
            if ("user".equals(entry.getRole())) {
                messages.add(new UserMessage(entry.getContent()));
            } else {
                messages.add(new AssistantMessage(entry.getContent()));
            }
        }

        // 4. Current user question
        messages.add(new UserMessage(question));

        return messages;
    }

    // ────────────────────────────────────────────
    // Inner classes
    // ────────────────────────────────────────────

    public static class ParsedPage {
        private final String text;
        private final int pageNumber;

        public ParsedPage(String text, int pageNumber) {
            this.text = text;
            this.pageNumber = pageNumber;
        }

        public String getText() { return text; }
        public int getPageNumber() { return pageNumber; }
    }

    public static class PendingChunk {
        private final String text;
        private final int index;
        private final int pageNumber;
        private float[] embedding;

        public PendingChunk(String text, int index, int pageNumber) {
            this.text = text;
            this.index = index;
            this.pageNumber = pageNumber;
        }

        public String getText() { return text; }
        public int getIndex() { return index; }
        public int getPageNumber() { return pageNumber; }
        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    }

    public static class HistoryEntry {
        private final String role;
        private final String content;

        public HistoryEntry(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }
}
