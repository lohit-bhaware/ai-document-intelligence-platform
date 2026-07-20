package com.docai.chat;

import com.docai.auth.User;
import com.docai.document.Document;
import com.docai.document.DocumentRepository;
import com.docai.rag.RagService;
import com.docai.shared.UserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.StreamingChatModel;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserResolver userResolver;
    @Mock
    private RagService ragService;
    @Mock
    private StreamingChatModel streamingChatModel;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatService chatService;

    private User mockUser;
    private Document mockDocument;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setEmail("test@example.com");

        mockDocument = new Document();
        mockDocument.setId(UUID.randomUUID());
        mockDocument.setUser(mockUser);
    }

    @Test
    void getHistory_NoConversation_ReturnsEmpty() {
        when(userResolver.resolve("test@example.com")).thenReturn(mockUser);
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));
        when(conversationRepository.findByDocumentIdAndUserId(mockDocument.getId(), mockUser.getId()))
                .thenReturn(Optional.empty());

        ChatHistoryResponse response = chatService.getHistory(mockDocument.getId(), "test@example.com");

        assertNotNull(response);
        assertEquals(0, response.getMessages().size());
    }
}
