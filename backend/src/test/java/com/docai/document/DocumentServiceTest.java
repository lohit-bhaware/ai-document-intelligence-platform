package com.docai.document;

import com.docai.auth.User;
import com.docai.shared.BadRequestException;
import com.docai.shared.ResourceNotFoundException;
import com.docai.shared.UserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserResolver userResolver;
    @Mock
    private StorageService storageService;
    @Mock
    private DocumentProcessingService documentProcessingService;

    @InjectMocks
    private DocumentService documentService;

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
        mockDocument.setFilename("test.pdf");
        mockDocument.setFileKey("uuid-test.pdf");
        mockDocument.setStatus("PENDING");
    }

    @Test
    void getDocument_Success() {
        when(userResolver.resolve("test@example.com")).thenReturn(mockUser);
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));

        DocumentDto result = documentService.getDocument(mockDocument.getId(), "test@example.com");

        assertNotNull(result);
        assertEquals("test.pdf", result.getFilename());
    }

    @Test
    void getDocument_AccessDenied() {
        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID());

        when(userResolver.resolve("hacker@example.com")).thenReturn(anotherUser);
        when(documentRepository.findById(mockDocument.getId())).thenReturn(Optional.of(mockDocument));

        assertThrows(AccessDeniedException.class, () -> 
            documentService.getDocument(mockDocument.getId(), "hacker@example.com")
        );
    }

    @Test
    void uploadDocument_EmptyFile_ThrowsException() {
        when(userResolver.resolve("test@example.com")).thenReturn(mockUser);
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        assertThrows(BadRequestException.class, () -> documentService.uploadDocument(file, "test@example.com"));
    }
}
