package com.docai.chat;

import com.docai.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final Executor documentTaskExecutor;

    @GetMapping("/{docId}/history")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory(
            @PathVariable UUID docId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        ChatHistoryResponse history = chatService.getHistory(docId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @DeleteMapping("/{docId}/history")
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @PathVariable UUID docId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        chatService.clearHistory(docId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * SSE streaming endpoint for document Q&A.
     * Returns text/event-stream with events: token, citations, done, error.
     * This is the ONLY endpoint that does not return ApiResponse<T> (per RULES.md §5).
     */
    @PostMapping(value = "/{docId}/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter query(
            @PathVariable UUID docId,
            @RequestBody ChatQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2-minute timeout

        // Run streaming in background thread to avoid blocking the servlet thread
        documentTaskExecutor.execute(() ->
                chatService.streamAnswer(docId, request.getMessage(), userDetails.getUsername(), emitter)
        );

        return emitter;
    }
}
