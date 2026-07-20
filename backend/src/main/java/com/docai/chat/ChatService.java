package com.docai.chat;

import com.docai.auth.User;
import com.docai.document.Document;
import com.docai.document.DocumentRepository;
import com.docai.rag.DocumentChunk;
import com.docai.rag.RagService;
import com.docai.shared.BadRequestException;
import com.docai.shared.ResourceNotFoundException;
import com.docai.shared.UserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final UserResolver userResolver;
    private final RagService ragService;
    private final StreamingChatModel streamingChatModel;
    private final ObjectMapper objectMapper;

    // ────────────────────────────────────────────
    // History endpoints
    // ────────────────────────────────────────────

    public ChatHistoryResponse getHistory(UUID docId, String userEmail) {
        User user = userResolver.resolve(userEmail);
        Document document = resolveDocument(docId, user);

        Conversation conversation = conversationRepository
                .findByDocumentIdAndUserId(document.getId(), user.getId())
                .orElse(null);

        if (conversation == null) {
            return ChatHistoryResponse.builder()
                    .conversationId(null)
                    .documentId(docId)
                    .messages(List.of())
                    .build();
        }

        List<MessageDto> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ChatHistoryResponse.builder()
                .conversationId(conversation.getId())
                .documentId(docId)
                .messages(messages)
                .build();
    }

    @Transactional
    public void clearHistory(UUID docId, String userEmail) {
        User user = userResolver.resolve(userEmail);
        Document document = resolveDocument(docId, user);

        conversationRepository.findByDocumentIdAndUserId(document.getId(), user.getId())
                .ifPresent(conversation -> {
                    messageRepository.deleteAllByConversationId(conversation.getId());
                });
    }

    // ────────────────────────────────────────────
    // Streaming query
    // ────────────────────────────────────────────

    public void streamAnswer(UUID docId, String question, String userEmail, SseEmitter emitter) {
        try {
            if (question == null || question.trim().isEmpty()) {
                sendErrorEvent(emitter, "Message cannot be empty");
                return;
            }

            User user = userResolver.resolve(userEmail);
            Document document = resolveDocument(docId, user);

            if (!"READY".equals(document.getStatus())) {
                sendErrorEvent(emitter, "Document is not ready for querying");
                return;
            }

            // Get or create conversation
            Conversation conversation = conversationRepository
                    .findByDocumentIdAndUserId(document.getId(), user.getId())
                    .orElseGet(() -> {
                        Conversation newConv = new Conversation();
                        newConv.setDocument(document);
                        newConv.setUser(user);
                        return conversationRepository.save(newConv);
                    });

            // Save user message
            ChatMessage userMsg = new ChatMessage();
            userMsg.setConversation(conversation);
            userMsg.setRole("user");
            userMsg.setContent(question.trim());
            userMsg.setCitations("[]");
            messageRepository.save(userMsg);

            // RAG: embed question + vector search (top 5 chunks)
            List<DocumentChunk> chunks = ragService.searchChunks(document.getId(), question, 5);

            // Get last 4 messages for conversation context
            List<ChatMessage> allMessages = messageRepository
                    .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
            List<RagService.HistoryEntry> recentHistory = getRecentHistory(allMessages, 4);

            // Build prompt (System → Context → History → User question)
            List<org.springframework.ai.chat.messages.Message> promptMessages = ragService.buildPromptMessages(chunks,
                    recentHistory, question);
            Prompt prompt = new Prompt(promptMessages);

            // Stream the response from Gemini
            StringBuilder fullResponse = new StringBuilder();

            Disposable subscription = streamingChatModel.stream(prompt).subscribe(
                    chatResponse -> {
                        try {
                            String token = extractToken(chatResponse);
                            if (token != null && !token.isEmpty()) {
                                fullResponse.append(token);
                                java.util.Map<String, String> tokenData = new java.util.HashMap<>();
                                tokenData.put("token", token);
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(objectMapper.writeValueAsString(tokenData)));
                            }
                        } catch (IOException e) {
                            // Client disconnected
                        }
                    },
                    error -> {
                        sendErrorEvent(emitter, error.getMessage() != null ? error.getMessage() : "Streaming failed");
                    },
                    () -> {
                        try {
                            // Build citations from the chunks that were used
                            String citationsJson = buildCitationsJson(chunks);

                            // Save assistant message to DB
                            ChatMessage assistantMsg = new ChatMessage();
                            assistantMsg.setConversation(conversation);
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(fullResponse.toString());
                            assistantMsg.setCitations(citationsJson);
                            ChatMessage savedMsg = messageRepository.save(assistantMsg);

                            // Send citations event
                            java.util.Map<String, Object> citationsEventData = new java.util.HashMap<>();
                            citationsEventData.put("citations", objectMapper.readTree(citationsJson));
                            emitter.send(SseEmitter.event()
                                    .name("citations")
                                    .data(objectMapper.writeValueAsString(citationsEventData)));

                            // Send done event
                            java.util.Map<String, String> doneData = new java.util.HashMap<>();
                            doneData.put("messageId", savedMsg.getId().toString());
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(objectMapper.writeValueAsString(doneData)));

                            emitter.complete();
                        } catch (IOException e) {
                            // Client disconnected
                        }
                    });

            // Clean up subscription if client disconnects
            emitter.onCompletion(subscription::dispose);
            emitter.onTimeout(() -> {
                subscription.dispose();
                emitter.complete();
            });

        } catch (AccessDeniedException e) {
            sendErrorEvent(emitter, "You do not have access to this document");
        } catch (ResourceNotFoundException e) {
            sendErrorEvent(emitter, e.getMessage());
        } catch (Exception e) {
            sendErrorEvent(emitter, "An unexpected error occurred");
        }
    }

    // ────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────

    private Document resolveDocument(UUID docId, User user) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }

        return document;
    }

    private List<RagService.HistoryEntry> getRecentHistory(List<ChatMessage> allMessages, int maxPairs) {
        // Get the last N messages (excluding the user message we just saved, which is
        // the last one)
        // We want the last 4 messages BEFORE the current question
        List<ChatMessage> history = allMessages.size() > 1
                ? allMessages.subList(0, allMessages.size() - 1) // exclude current user message
                : List.of();

        int start = Math.max(0, history.size() - (maxPairs * 2));
        List<ChatMessage> recent = history.subList(start, history.size());

        return recent.stream()
                .map(m -> new RagService.HistoryEntry(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private String extractToken(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null ||
                chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getContent();
    }

    private String buildCitationsJson(List<DocumentChunk> chunks) {
        try {
            List<java.util.Map<String, Object>> citations = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("chunkIndex", chunk.getChunkIndex());
                map.put("pageNumber", chunk.getPageNumber());
                citations.add(map);
            }
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            java.util.Map<String, String> errorData = new java.util.HashMap<>();
            errorData.put("message", message);
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(errorData)));
            emitter.complete();
        } catch (Exception e) {
            // Client already disconnected
        }
    }

    private MessageDto mapToDto(ChatMessage message) {
        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .citations(message.getCitations())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
