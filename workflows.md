# Workflows — AI Document Q&A Platform

Place each workflow below as its own `.md` file inside `.agents/workflows/`.
Invoke any workflow by typing `/workflow-name` in the Antigravity agent chat.

---

## Index

| Slash Command | File | What it does |
|---|---|---|
| `/new-endpoint` | `new-endpoint.md` | Scaffold a new REST endpoint end-to-end |
| `/new-component` | `new-component.md` | Scaffold a new React component + wire it to the API |
| `/new-migration` | `new-migration.md` | Create a new Flyway SQL migration file |
| `/new-service` | `new-service.md` | Add a new Spring Boot service method |
| `/add-rag-step` | `add-rag-step.md` | Extend or modify the RAG pipeline |
| `/debug-api` | `debug-api.md` | Debug a failing REST endpoint |
| `/debug-sse` | `debug-sse.md` | Debug a broken SSE stream |
| `/check-auth` | `check-auth.md` | Verify auth and ownership are correctly applied |

---

## Workflow Files

---

### `/new-endpoint` — `new-endpoint.md`

```md
## Workflow: New REST Endpoint

Use this when adding a new API route to the Spring Boot backend.

### Steps

1. **Identify the layer**
   Confirm which controller this belongs to:
   - Auth-related → `AuthController.java`
   - File/document-related → `DocumentController.java`
   - Chat/query-related → `ChatController.java`
   Never create a new controller unless a feature is entirely new.

2. **Add the controller method**
   - Annotate with the correct HTTP method: `@GetMapping`, `@PostMapping`, `@DeleteMapping`
   - Method must return `ResponseEntity<ApiResponse<T>>`
   - Read inputs from `@RequestBody`, `@PathVariable`, or `@RequestParam` only
   - No logic here — immediately delegate to the service

   Template:
   \```java
   @PostMapping("/your-path")
   public ResponseEntity<ApiResponse<YourDto>> methodName(
       @RequestBody YourRequest request,
       @AuthenticationPrincipal UserDetails userDetails
   ) {
       YourDto result = yourService.doSomething(request, userDetails.getUsername());
       return ResponseEntity.ok(ApiResponse.success(result));
   }
   \```

3. **Add the service method**
   - All business logic goes here
   - If touching multiple tables, annotate the method with `@Transactional`
   - Always verify the resource belongs to the current user before operating on it

   Template:
   \```java
   @Transactional
   public YourDto doSomething(YourRequest request, String userEmail) {
       User user = userRepository.findByEmail(userEmail)
           .orElseThrow(() -> new ResourceNotFoundException("User not found"));
       // business logic here
   }
   \```

4. **Add the repository method (if needed)**
   - Add a query method to the relevant `JpaRepository` interface
   - Use Spring Data method naming or `@Query` for complex SQL

5. **Create DTO classes (if needed)**
   - Input: `YourRequest.java` (what the client sends)
   - Output: `YourDto.java` (what the API returns)
   - Never return a raw entity to the client

6. **Verify the endpoint is secured**
   - Confirm it is NOT listed under `.permitAll()` in `SecurityConfig.java`
   - If it should be public (only `/api/auth/**` routes are public), explicitly add it

7. **Test manually**
   - Hit the endpoint with a sample request
   - Confirm response shape matches `{ success: true, data: {...}, error: null }`
   - Confirm a request without a JWT returns 401
   - Confirm a request for another user's resource returns 403
```

---

### `/new-component` — `new-component.md`

```md
## Workflow: New React Component

Use this when adding a new UI component to the frontend.

### Steps

1. **Decide where it lives**
   - Reusable UI piece (used in multiple places) → `src/components/`
   - Full page (maps to a route) → `src/pages/`
   - Never create a page inside `src/components/` or vice versa

2. **Create the file**
   - Filename must match the component name exactly: `DocumentCard.jsx` exports `DocumentCard`
   - One component per file, no exceptions

3. **Write the component**
   - Use functional components with hooks
   - Props must be clearly named — no single-letter props
   - No `fetch` calls inside the component

   Template:
   \```jsx
   function DocumentCard({ document, onDelete }) {
     return (
       <div className="document-card">
         <span>{document.filename}</span>
         <span>{document.status}</span>
         <button onClick={() => onDelete(document.id)}>Delete</button>
       </div>
     );
   }

   export default DocumentCard;
   \```

4. **Wire API calls through `src/api/client.js`**
   - If the component needs data from the backend, the fetch goes in `client.js`
   - Call the `client.js` function from the component — never inline `fetch`

   \```js
   // In client.js — add the function
   export async function deleteDocument(id) {
     const res = await fetch(`${API_URL}/documents/${id}`, {
       method: "DELETE",
       headers: { Authorization: `Bearer ${getToken()}` },
     });
     return res.json();
   }

   // In the component — call it
   import { deleteDocument } from "../api/client";

   function DocumentCard({ document, onDelete }) {
     const handleDelete = async () => {
       await deleteDocument(document.id);
       onDelete(document.id);
     };
     // ...
   }
   \```

5. **Handle loading and error states**
   - Every component that fetches data must have a loading state and an error state
   - Never render an empty screen while loading — show a visible loading indicator

6. **Import and use it**
   - Import the component in its parent page
   - Pass only the props it needs — no over-sharing of state
```

---

### `/new-migration` — `new-migration.md`

```md
## Workflow: New Database Migration

Use this when the database schema needs to change (new table, new column, new index).

### Steps

1. **Find the next migration number**
   Look in `src/main/resources/db/migration/` and find the highest version number.
   If the last file is `V3__add_index.sql`, the new one is `V4`.

2. **Create the file**
   File must follow this exact naming format:
   ```
   src/main/resources/db/migration/V{N}__{description}.sql
   ```
   Use snake_case for the description. Examples:
   ```
   V2__add_page_count_to_documents.sql
   V3__create_conversations_table.sql
   V4__add_index_on_user_id.sql
   ```

3. **Write the SQL**
   - Never use `DROP TABLE` or `DROP COLUMN` in a migration — data loss
   - Use `ALTER TABLE ... ADD COLUMN` for new columns
   - New columns on existing tables must have a DEFAULT or be nullable
   - Always add `IF NOT EXISTS` on `CREATE TABLE` and `CREATE INDEX`

   Example:
   \```sql
   -- V2__add_page_count_to_documents.sql
   ALTER TABLE documents
     ADD COLUMN IF NOT EXISTS page_count INT DEFAULT 0;
   \```

4. **Verify it runs**
   Restart Spring Boot. Flyway runs all pending migrations on startup automatically.
   Check the logs for:
   ```
   Successfully applied 1 migration to schema "public"
   ```
   If you see an error, fix the SQL — never delete a migration file that has already run.

5. **Never edit a migration that has already run**
   If a migration has been applied (locally or in prod), it is locked.
   To fix it, create a new migration that corrects the previous one.
```

---

### `/new-service` — `new-service.md`

```md
## Workflow: New Spring Boot Service Method

Use this when adding business logic to an existing service.

### Steps

1. **Find the right service**
   - User/auth logic → `AuthService.java`
   - Document upload/delete/status → `DocumentService.java`
   - Chat history, message persistence → `ChatService.java`
   - RAG: chunking, embedding, vector search, prompting → `RagService.java`

2. **Write the method**
   - Private helper logic → private method in the same class
   - If the method touches more than one table → add `@Transactional`
   - Always validate ownership: fetch the resource, confirm `userId` matches the caller
   - Throw named exceptions on failure — never return null to signal an error

   \```java
   @Transactional
   public void deleteDocument(UUID documentId, String userEmail) {
       User user = userRepository.findByEmail(userEmail)
           .orElseThrow(() -> new ResourceNotFoundException("User not found"));

       Document doc = documentRepository.findById(documentId)
           .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

       if (!doc.getUserId().equals(user.getId())) {
           throw new AccessDeniedException("You do not own this document");
       }

       // delete R2 file, chunks, conversation, then the document
       r2Service.deleteFile(doc.getFileKey());
       documentChunkRepository.deleteAllByDocumentId(documentId);
       conversationRepository.deleteByDocumentId(documentId);
       documentRepository.delete(doc);
   }
   \```

3. **Call it from the controller**
   The controller calls the service method and wraps the result in `ApiResponse`.
   No logic in the controller — just delegation.

4. **Check exception handling**
   Confirm `GlobalExceptionHandler.java` handles any new exception types you throw.
   If you add a new exception class, add a corresponding `@ExceptionHandler` method.
```

---

### `/add-rag-step` — `add-rag-step.md`

```md
## Workflow: Extend or Modify the RAG Pipeline

Use this when changing how documents are parsed, chunked, embedded, or how queries are answered.

### Where the RAG code lives

```
src/main/java/com/docai/rag/
├── RagService.java          ← orchestrates the full query flow
├── ChunkingService.java     ← splits parsed text into chunks
└── EmbeddingService.java    ← calls OpenAI embedding API
```

Document ingestion is triggered from `DocumentService.java` via an `@Async` method.

### RAG rules — never break these

- Chunk size: **800 tokens, 100 token overlap**. Do not change without a reason.
- Embedding model: **always `text-embedding-3-small`** for both ingestion AND queries. Mixing models silently breaks similarity search.
- Vector search: **always filter by `document_id`**. Never search across all documents globally.
- Prompt order: **System → Context chunks → Conversation history → User question**. Never change this order.
- Batch size: **50 chunks per OpenAI embedding request**. Never embed one chunk at a time in a loop.
- Top-K: retrieve **5 chunks** per query. Not 3, not 10 — 5.

### Steps to modify ingestion (parse → chunk → embed → store)

1. Locate the `@Async` method in `DocumentService.java` that triggers processing
2. Make changes in the relevant service (`ChunkingService` for chunking, `EmbeddingService` for embedding)
3. If changing chunk size or overlap, update the constants — do not hardcode numbers inline
4. After changing ingestion logic, re-process an existing document to verify chunks look correct
5. Confirm `document.status` updates correctly: PENDING → PROCESSING → READY or FAILED

### Steps to modify the query pipeline

1. Open `RagService.java`
2. The query flow is: rate-limit check → cache check → embed query → pgvector search → build prompt → stream LLM
3. If modifying the prompt, keep the order: System → Context → History → User question
4. If modifying the pgvector query, always keep `WHERE document_id = :docId`
5. Test with a real question — confirm cited chunk numbers match actual document content

### After any RAG change

- Test with at least 3 different questions on a real document
- Confirm citations (`chunkIndex`, `pageNumber`) are correct in the SSE `citations` event
- Confirm the answer is actually grounded in the retrieved chunks, not hallucinated
```

---

### `/debug-api` — `debug-api.md`

```md
## Workflow: Debug a Failing REST Endpoint

Use this when an API call returns an unexpected response or error.

### Steps

1. **Check the HTTP status code first**
   - `401` → JWT is missing, expired, or malformed. Check the `Authorization` header.
   - `403` → JWT is valid but the resource doesn't belong to this user. Check ownership logic in the service.
   - `404` → Resource not found. Check the ID being passed and the DB record.
   - `400` → Bad request. Check request body format and validation.
   - `500` → Server error. Check Spring Boot logs immediately.

2. **Read the Spring Boot logs**
   The error message and stack trace are in the console where `./mvnw spring-boot:run` is running.
   Look for the first line that says `ERROR` — that is the root cause.

3. **Check the response body**
   All errors return:
   \```json
   { "success": false, "data": null, "error": "message here" }
   \```
   If the response is not this shape, the error is happening before `GlobalExceptionHandler` catches it
   (likely a Spring Security rejection — check `SecurityConfig.java`).

4. **Check `SecurityConfig.java`**
   Verify the failing route is not accidentally listed under `.permitAll()` (if it should be protected)
   or missing from `.permitAll()` (if it is `/api/auth/**`).

5. **Check the service method**
   - Is ownership being verified? (`doc.getUserId().equals(user.getId())`)
   - Is `@Transactional` present on methods that touch multiple tables?
   - Is the correct exception being thrown (`ResourceNotFoundException`, `AccessDeniedException`)?

6. **Check the database**
   Connect to local Postgres (`docker compose up -d`) and run the query manually.
   If the query returns nothing, the data isn't there — the issue is in the write path, not the read path.
```

---

### `/debug-sse` — `debug-sse.md`

```md
## Workflow: Debug a Broken SSE Stream

Use this when the chat query fires but tokens don't appear in the browser, or the stream cuts off early.

### Steps

1. **Check the browser Network tab**
   Open DevTools → Network → filter by `EventStream`.
   Find the `POST /api/chat/:docId/query` request.
   - If status is not `200` → the error is in the controller before the stream starts. Check Spring Boot logs.
   - If status is `200` but no events arrive → the stream opened but nothing was emitted. Go to step 3.
   - If events arrive then stop suddenly → the stream errored mid-way. Check for an `error` event in the stream.

2. **Check the React `EventSource` code**
   In `src/api/client.js` or the chat component, confirm:
   - The URL is correct: `` `${API_URL}/chat/${docId}/query` ``
   - All three event types are handled: `token`, `citations`, `done`, `error`
   - `source.close()` is called on both `done` and `error`
   - The Send button is disabled while `streaming === true`

3. **Check the Spring Boot `SseEmitter`**
   In `ChatController.java`, confirm:
   - The method returns `SseEmitter`, not `ResponseEntity`
   - The emitter timeout is set high enough (e.g. `new SseEmitter(180_000L)` for 3 minutes)
   - `emitter.complete()` is called after the stream finishes
   - `emitter.completeWithError(e)` is called in the catch block

4. **Check the OpenAI streaming call**
   In `RagService.java`, confirm:
   - The Spring AI `ChatClient` is called with `.stream()` not `.call()`
   - Each token from the stream is sent as: `emitter.send(SseEmitter.event().name("token").data(...))`
   - The `citations` event is sent after the stream completes, before `done`

5. **Check CORS**
   If the frontend and backend run on different ports locally (React on 3000, Spring on 8080),
   confirm `CorsConfig.java` allows `http://localhost:3000` and that `text/event-stream` is not blocked.
```

---

### `/check-auth` — `check-auth.md`

```md
## Workflow: Verify Auth and Ownership Are Correctly Applied

Use this before finishing any endpoint that reads or modifies user data.

### Checklist — run through every item

**JWT layer (Spring Security)**
- [ ] The route is NOT under `.permitAll()` in `SecurityConfig.java`
- [ ] The JWT filter (`JwtAuthFilter.java`) is in the filter chain before the controller runs
- [ ] A request without a token returns `401 Unauthorized`
- [ ] A request with an expired token returns `401 Unauthorized`
- [ ] A request with a tampered token returns `401 Unauthorized`

**Ownership layer (Service)**
- [ ] The service fetches the resource from DB using the ID from the request
- [ ] The service fetches the current user from DB using the email from the JWT (`userDetails.getUsername()`)
- [ ] The service compares `resource.getUserId()` with `user.getId()`
- [ ] If they don't match, the service throws `AccessDeniedException` (not `ResourceNotFoundException`)
  - Note: never say "not found" when the real reason is "not yours" — that leaks information
- [ ] A request for another user's resource returns `403 Forbidden`

**Test it manually**
1. Register two users: User A and User B
2. User A uploads a document → get the document ID
3. Log in as User B → get User B's token
4. Try to GET / DELETE User A's document using User B's token
5. Confirm the response is `403`, not `200` or `404`
```
