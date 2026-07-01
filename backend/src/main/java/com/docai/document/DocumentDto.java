package com.docai.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private UUID id;
    private String filename;
    private Long fileSize;
    private String mimeType;
    private Integer chunkCount;
    private String status;
    private String errorMsg;
    private OffsetDateTime createdAt;
}
