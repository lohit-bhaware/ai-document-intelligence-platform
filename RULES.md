# RULES.md — Development Rules for AI Document Q&A Platform

These rules exist so that every file, every endpoint, and every component
feels like it was written by one person. Follow them every time — no exceptions.

---

## 1. Project Structure

```
/
├── backend/                  ← Spring Boot (Maven)
│   └── src/main/java/com/docai/
│       ├── auth/
│       │   ├── AuthController.java
│       │   ├── AuthService.java
│       │   └── AuthRepository.java
│       ├── document/
│       │   ├── DocumentController.java
│       │   ├── DocumentService.java
│       │   └── DocumentRepository.java
│       ├── chat/
│       │   ├── ChatController.java
│       │   ├── ChatService.java
│       │   └── MessageRepository.java
│       ├── rag/
│       │   ├── RagService.java
│       │   ├── ChunkingService.java
│       │   └── EmbeddingService.java
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   └── AsyncConfig.java
│       └── shared/
│           ├── ApiResponse.java     ← standard response wrapper
│           └── GlobalExceptionHandler.java
│
├── frontend/                 ← React.js (Create React App or Vite)
│   └── src/
│       ├── pages/
│       │   ├── LoginPage.jsx
│       │   ├── DocumentsPage.jsx
│       │   └── ChatPage.jsx
│       ├── components/
│       │   ├── DocumentCard.jsx
│       │   ├── MessageBubble.jsx
│       │   └── UploadZone.jsx
│       ├── api/
│       │   └── client.js          ← all fetch calls live here
│       └── App.jsx
│
├── docker-compose.yml
└── RULES.md
```

**Rules:**
- One folder per feature (`auth`, `document`, `chat`, `rag`). Never mix features.
- Controller → Service → Repository. Never skip a layer. Never call a Repository from a Controller directly.
- Shared utilities go in `/shared`. Nothing else goes there.
- All API calls in the frontend live in `src/api/client.js`. Components never call `fetch` directly.

---

## 2. Backend Rules (Spring Boot)

### Package naming
```
com.docai.auth
com.docai.document
com.docai.chat
com.docai.rag
com.docai.config
com.docai.shared
```

### Controller rules
- Controllers only handle HTTP: read the request, call a service, return a response.
- No business logic in controllers. No database calls in controllers.
- Every controller method returns `ResponseEntity<ApiResponse<T>>`.
- Map all endpoints under `/api/**`.

```java
// Good
@PostMapping("/upload")
public ResponseEntity<ApiResponse<DocumentDto>> upload(...) {
    DocumentDto doc = documentService.upload(...);
    return ResponseEntity.ok(ApiResponse.success(doc));
}

// Bad — business logic in controller
@PostMapping("/upload")
public ResponseEntity<?> upload(...) {
    // parsing, saving, embedding all in here — NO
}
```

### Standard API response wrapper
Every endpoint returns this shape — success or error:

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;

    public static <T> ApiResponse<T> success(T data) { ... }
    public static <T> ApiResponse<T> error(String message) { ... }
}
```

```json
// Success
{ "success": true, "data": { ... }, "error": null }

// Error
{ "success": false, "data": null, "error": "Document not found" }
```

### Service rules
- All business logic lives in services.
- Services are `@Transactional` where the method touches multiple tables.
- Document processing (parse → chunk → embed → store) runs in an `@Async` method.
- Never call one controller from another. Share logic through services.

### Exception handling
- Use a single `@RestControllerAdvice` class (`GlobalExceptionHandler`) to catch all exceptions.
- Never return a 200 with an error message inside. Use proper HTTP status codes.
- Never let a stack trace reach the client.

```java
// Good
throw new ResourceNotFoundException("Document not found");

// Bad
return ResponseEntity.ok("error: document not found");
```

### HTTP status codes — use these consistently
| Situation | Status |
|---|---|
| Success (data returned) | 200 |
| Created successfully | 201 |
| Bad request / validation error | 400 |
| Not authenticated | 401 |
| Authenticated but not allowed | 403 |
| Resource not found | 404 |
| Server error | 500 |

### Naming conventions
- Classes: `PascalCase` — `DocumentService`, `AuthController`
- Methods and variables: `camelCase` — `uploadDocument`, `userId`
- Constants: `UPPER_SNAKE_CASE` — `MAX_FILE_SIZE`
- Database columns in SQL: `snake_case` — `user_id`, `created_at`
- REST endpoints: `kebab-case` — `/api/documents/upload`, `/api/chat/:docId/history`

### Security rules
- Every route except `/api/auth/**` must be protected by the JWT filter.
- Always check that the resource belongs to the requesting user before returning it. Never trust the client.
- Passwords: always BCrypt. Never log a password. Never store plain text.
- Never expose internal IDs or stack traces in error responses.

```java
// Always verify ownership
Document doc = documentRepository.findById(docId)
    .orElseThrow(() -> new ResourceNotFoundException("Not found"));

if (!doc.getUserId().equals(currentUserId)) {
    throw new AccessDeniedException("Forbidden");
}
```

### Configuration
- All secrets and environment-specific values go in `application.properties` via environment variables. No hardcoded values.

```properties
# Good
openai.api.key=${OPENAI_API_KEY}
app.jwt.secret=${JWT_SECRET}

# Bad
openai.api.key=sk-abc123
```

---

## 3. RAG Pipeline Rules

- The RAG pipeline lives entirely in the `rag` package. Nothing outside this package should know how chunking or embedding works.
- Chunk size: **800 tokens, 100 token overlap**. Do not change this without a reason.
- Embedding model: **OpenAI text-embedding-3-small**. Always the same model for both ingestion and queries — mixing models breaks similarity search.
- Vector search: always filter by `document_id` first. Never search across all documents.
- Prompt structure order: System → Context chunks → Conversation history → User question. Never change this order.
- Always include the chunk index and page number when storing a chunk. These are needed for citations.
- Batch embedding calls: 50 chunks per OpenAI request. Never embed one chunk at a time in a loop.
- If document processing fails, set `status = FAILED` and store the error message. Never silently swallow errors.

---

## 4. Database Rules

- All schema changes are SQL files in `src/main/resources/db/migration/` named `V1__init.sql`, `V2__add_column.sql`, etc. Run via Flyway on startup.
- Never alter tables manually in production. Always use a migration file.
- Every table has: a UUID primary key, a `created_at` timestamp.
- Foreign keys always have `ON DELETE CASCADE` where deleting the parent should remove children.
- Never store a raw file in the database. Store the file in R2, store the R2 key in the DB.
- Never store a raw password. Store the BCrypt hash.

```sql
-- Good
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
created_at TIMESTAMPTZ DEFAULT now()

-- Bad
id SERIAL PRIMARY KEY
```

---

## 5. REST API Rules

- All endpoints are prefixed `/api`.
- Use nouns for resources, not verbs.

```
Good:  POST /api/documents/upload
Bad:   POST /api/uploadDocument
```

- Use the correct HTTP method:
  - `GET` — read data, no side effects
  - `POST` — create something or trigger an action
  - `DELETE` — remove something
- Endpoint naming: `kebab-case` only.
- The SSE streaming endpoint (`POST /api/chat/:docId/query`) is the only endpoint that does not return `ApiResponse<T>`. It returns `text/event-stream`. Document this clearly wherever it's used.
- Never return different response shapes from the same endpoint depending on success/failure. Always use `ApiResponse`.

---

## 6. Frontend Rules

### Component rules
- One component per file. Filename matches component name exactly: `DocumentCard.jsx` exports `DocumentCard`.
- Components only handle rendering and user interaction. No `fetch` calls inside components.
- All API calls go through `src/api/client.js`.

```js
// Good — in client.js
export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(`${API_URL}/documents/upload`, {
    method: "POST",
    headers: { Authorization: `Bearer ${getToken()}` },
    body: formData,
  });
  return res.json();
}

// Bad — fetch inside a component
function DocumentPage() {
  const handleUpload = async () => {
    const res = await fetch("/api/documents/upload", ...); // NO
  };
}
```

### JWT handling
- Store the JWT in `localStorage` under the key `"token"`.
- Every API request sends `Authorization: Bearer <token>` header.
- If any request returns 401, clear the token and redirect to `/login`.
- Put this logic once in `client.js`. Never repeat it in components.

### SSE / streaming
- Use the browser's native `EventSource` for the chat stream. No extra library.
- Handle these events: `token`, `citations`, `done`, `error`.
- Disable the Send button while a stream is in progress.
- On `error` event: show the error message, re-enable the Send button.

```js
// Standard SSE pattern for this project
const source = new EventSource(`${API_URL}/chat/${docId}/query?...`);

source.addEventListener("token", (e) => {
  const { token } = JSON.parse(e.data);
  setCurrentMessage((prev) => prev + token);
});

source.addEventListener("done", () => {
  source.close();
  setStreaming(false);
});

source.addEventListener("error", (e) => {
  source.close();
  setStreaming(false);
  setError(JSON.parse(e.data).message);
});
```

### Routing
Three routes, nothing else:

| Path | Page |
|---|---|
| `/login` | LoginPage |
| `/documents` | DocumentsPage |
| `/chat/:docId` | ChatPage |

- If the user is not logged in, redirect to `/login`.
- If the user is logged in and visits `/login`, redirect to `/documents`.

### State management
- Use React's built-in `useState` and `useEffect`. No external state library.
- Document list, current user, and JWT: keep in component state or pass as props.
- If state needs to be shared between pages, lift it up to `App.jsx`.

### Naming conventions
- Components: `PascalCase` — `DocumentCard`, `MessageBubble`
- Functions and variables: `camelCase` — `handleUpload`, `documentList`
- Files: match the component name — `DocumentCard.jsx`
- CSS class names: `kebab-case` — `.document-card`, `.message-bubble`

---

## 7. Git Rules

### Branch naming
```
feature/document-upload
feature/rag-pipeline
feature/chat-streaming
fix/auth-token-expiry
```

### Commit message format
```
type: short description

feat: add document upload endpoint
fix: handle PDF parsing failure gracefully
refactor: move embedding logic into EmbeddingService
docs: update README with local setup steps
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

### Rules
- One logical change per commit. Don't bundle unrelated changes.
- Never commit secrets, `.env` files, or API keys.
- Always commit a working state. Don't push broken code to `main`.
- `main` branch = production. Do all work on feature branches, merge when done.

---

## 8. What Not To Do

These are the most common ways this project could get complicated. Don't do any of these:

- **Don't add a library without a clear reason.** Every dependency is something to maintain.
- **Don't add endpoints that aren't in the PRD.** Build what's planned first.
- **Don't put logic in the wrong layer.** Controllers do HTTP. Services do logic. Repositories do data.
- **Don't return different shapes from the same endpoint.** Always use `ApiResponse<T>`.
- **Don't hardcode any URL, secret, or environment-specific value.** Always use environment variables.
- **Don't mix features.** Auth code stays in `auth/`. RAG code stays in `rag/`.
- **Don't use a different embedding model for queries than you used for ingestion.** They must match.
- **Don't call `fetch` inside a React component.** All API calls go through `src/api/client.js`.
- **Don't commit to `main` directly.** Use feature branches.

---

*These rules apply to every file in this project. When in doubt, check the PRD first, then come back here.*
