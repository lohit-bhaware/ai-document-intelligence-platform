package com.docai.rag;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    // 1 token is roughly 4 characters in English
    private static final int CHARS_PER_TOKEN = 4;
    private static final int CHUNK_SIZE_CHARS = 800 * CHARS_PER_TOKEN; // 3200 characters
    private static final int OVERLAP_CHARS = 100 * CHARS_PER_TOKEN; // 400 characters

    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE_CHARS, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += (CHUNK_SIZE_CHARS - OVERLAP_CHARS);
        }
        return chunks;
    }
}
