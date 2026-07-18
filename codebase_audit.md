# Codebase Re-Audit — July 17, 2026

**Scope:** Every file in `backend/` and `frontend/` vs. [PRD](file:///d:/ai-document-intelligence-platform/PRD_Document_Intelligence_Platform%20(3).md) + [RULES.md](file:///d:/ai-document-intelligence-platform/RULES.md)  
**Previous audit:** Conducted earlier today. This is a fresh, full review after Phase 1–3 changes.

---

## 1. Fully Implemented ✅

### Backend — Auth (`com.docai.auth`)
| Item | PRD Ref | Status |
|---|---|---|
| `POST /api/auth/register` | §7 | ✅ Returns `{ token, user }` |
| `POST /api/auth/login` | §7 | ✅ Returns `{ token, user }` |
| BCrypt password hashing | §5 | ✅ Via `PasswordEncoder` bean |
| JWT generation & validation | §3 | ✅ [JwtService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/config/JwtService.java) + [JwtAuthFilter](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/config/JwtAuthFilter.java) |
| All non-auth routes require JWT | §7 | ✅ `.requestMatchers("/api/auth/**").permitAll()` |
| `ApiResponse<T>` wrapper on all endpoints | RULES §2 | ✅ Consistent across auth/doc/chat |
| `GlobalExceptionHandler` | RULES §2 | ✅ Handles 400/401/403/404/500, uses SLF4J Logger |
| Input validation (null/empty checks) | RULES §2 | ✅ In AuthService |

### Backend — Documents (`com.docai.document`)
| Item | PRD Ref | Status |
|---|---|---|
| `GET /api/documents` | §7 | ✅ Ordered by `created_at` desc |
| `POST /api/documents/upload` | §7 | ✅ Validates type (PDF/TXT), size (10MB), stores to storage |
| `GET /api/documents/:id` | §7 | ✅ With ownership check |
| `DELETE /api/documents/:id` | §7 | ✅ Deletes file + DB (cascade handles chunks/convos) |
| Ownership verification | §5 | ✅ Every endpoint checks `user.getId().equals(doc.getUser().getId())` |
| 10MB file size limit | §5 | ✅ Both `application.properties` and service-level |
| `PENDING → PROCESSING → READY / FAILED` | §5 | ✅ Status tracked in [DocumentProcessingService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/document/DocumentProcessingService.java) |
| `@Async` background processing | §4 | ✅ Separate service, avoids AOP proxy self-invocation |
| Transaction-safe async kickoff | §4 | ✅ Uses `TransactionSynchronization.afterCommit()` |
| StorageService interface | §3 | ✅ Clean abstraction for swap between Local/R2 |
| Local file storage with path traversal protection | §3 | ✅ [LocalStorageService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/document/LocalStorageService.java) with `@ConditionalOnProperty` |
| SLF4J logging (no `e.printStackTrace()`) | RULES §2 | ✅ Fixed — uses `log.error()` with context |

### Backend — RAG Pipeline (`com.docai.rag`)
| Item | PRD Ref | Status |
|---|---|---|
| PDF parsing (PDFBox, page-by-page) | §8 Step 1 | ✅ [ParsingService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/ParsingService.java) |
| TXT parsing | §8 Step 1 | ✅ `Files.readString` fallback |
| Chunking (~800 token / 100 overlap) | §8 Step 2 | ✅ [ChunkingService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/ChunkingService.java) — char approximation (×4) |
| Batch embedding (50/batch) | §8 Step 3 | ✅ [EmbeddingService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/EmbeddingService.java) |
| Vector storage in pgvector | §8 Step 4 | ✅ [RagPersistenceService](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/RagPersistenceService.java) with `@Transactional` |
| Cosine similarity search (top 5, filtered by doc) | §8 Step 5 | ✅ [DocumentChunkRepository](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/DocumentChunkRepository.java) native query |
| Chunk index + page number stored | §8 Step 2 | ✅ In [DocumentChunk](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/DocumentChunk.java) entity |
| Vector dimension matches Gemini (768) | §8 Step 3 | ✅ `@Array(length = 768)` + V2 migration |
| Failed processing sets `status=FAILED` | §5 | ✅ In catch block with error message |

### Backend — Chat (`com.docai.chat`)
| Item | PRD Ref | Status |
|---|---|---|
| `GET /api/chat/:docId/history` | §7 | ✅ Returns conversation + messages |
| `DELETE /api/chat/:docId/history` | §7 | ✅ Clears messages with `@Transactional` |
| `POST /api/chat/:docId/query` — SSE streaming | §7 | ✅ Full `SseEmitter` implementation |
| Prompt: System → Context → History → Question | §8 Step 6 | ✅ [RagService.buildPromptMessages](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/rag/RagService.java#L64-L100) |
| Last 4 messages as context | §8 Step 6 | ✅ `getRecentHistory(allMessages, 4)` |
| SSE events: `token`, `citations`, `done`, `error` | §7 | ✅ All four event types present |
| Save full answer + citations to DB on completion | §8 Step 7 | ✅ In onComplete handler |
| One conversation per document per user | §5 | ✅ `UNIQUE(document_id, user_id)` + get-or-create |
| Document must be READY to query | §5 | ✅ Status check before processing |

### Database
| Item | PRD Ref | Status |
|---|---|---|
| Flyway migration `V1__init.sql` | §6 | ✅ Matches PRD schema |
| `V2__fix_vector_dimension.sql` | — | ✅ Fixes 1536→768 for Gemini |
| pgvector extension + ivfflat index | §6 | ✅ |
| All tables: UUID PK + `created_at` | §6 | ✅ |
| `ON DELETE CASCADE` on all FKs | §6 | ✅ |
| `docker-compose.yml` (pgvector:pg16) | §10 | ✅ |

### Infrastructure / Config
| Item | PRD Ref | Status |
|---|---|---|
| `application.properties` — all env-var driven | RULES §2 | ✅ No hardcoded secrets |
| CORS origins from `${FRONTEND_URL}` env var | §10 | ✅ Fixed — was hardcoded, now configurable |
| Async thread pool configured | §4 | ✅ [AsyncConfig](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/config/AsyncConfig.java) |
| Gemini via OpenAI-compatible endpoint | §3 | ✅ Deliberate migration from OpenAI |
| R2 config properties exist | §10 | ✅ `app.r2.*` properties defined |
| `LocalStorageService` conditional on `app.r2.enabled=false` | §3 | ✅ `@ConditionalOnProperty` added |

### Frontend — Auth
| Item | PRD Ref | Status |
|---|---|---|
| Login/Register form (toggle) | §9 Screen 1 | ✅ [LoginPage.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/pages/LoginPage.jsx) |
| JWT stored in `localStorage` | §9 | ✅ In [client.js](file:///d:/ai-document-intelligence-platform/frontend/src/api/client.js) |
| 401 → auto-redirect to `/login` | §9 | ✅ In `request()` function |
| Protected/Public route guards | §9 | ✅ [App.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/App.jsx) |
| All API calls in `client.js` | RULES §1 | ✅ No `fetch` calls in components |

### Frontend — Documents
| Item | PRD Ref | Status |
|---|---|---|
| Document list with status badges | §9 Screen 2 | ✅ [DocumentsPage.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/pages/DocumentsPage.jsx) |
| Upload via drag-and-drop + click | §9 Screen 2 | ✅ [UploadZone.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/components/UploadZone.jsx) |
| Status polling every 3s until READY | §9 Screen 2 | ✅ `useEffect` with `setInterval` |
| Delete with confirmation | §9 Screen 2 | ✅ `window.confirm()` |
| "Chat" button → `/chat/:docId` | §9 Screen 2 | ✅ Only enabled when READY |
| Client-side file validation (type + size) | §5 | ✅ In UploadZone |
| FAILED status shows error message | §9 Screen 2 | ✅ In [DocumentCard.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/components/DocumentCard.jsx) |
| Upload progress — honest indeterminate animation | — | ✅ Fixed — was fake 70% bar |

### Frontend — Chat
| Item | PRD Ref | Status |
|---|---|---|
| Full ChatPage (not placeholder) | §9 Screen 3 | ✅ [ChatPage.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/pages/ChatPage.jsx) — 215 lines |
| Load existing chat history on mount | §9 Screen 3 | ✅ `getChatHistory(docId)` on useEffect |
| Message thread (user right, assistant left) | §9 Screen 3 | ✅ [MessageBubble.jsx](file:///d:/ai-document-intelligence-platform/frontend/src/components/MessageBubble.jsx) |
| SSE streaming — tokens render word-by-word | §9 Screen 3 | ✅ `streamQuery()` in client.js + `streamingContent` state |
| Source citations below assistant messages | §9 Screen 3 | ✅ Citation tags with chunk index + page number |
| Text input + Send button at bottom | §9 Screen 3 | ✅ Textarea + btn-send |
| Send button disabled during streaming | §9 Screen 3 | ✅ `disabled={isStreaming}` |
| "Clear history" button | §9 Screen 3 | ✅ With confirmation dialog |
| Back button to document list | §9 Screen 3 | ✅ `← Back` link to `/documents` |
| Error display for failed queries | §9 Screen 3 | ✅ Dismissable error banner |
| SSE via `fetch` + `ReadableStream` | §7 | ✅ Correct approach (POST body incompatible with native EventSource) |
| Auto-scroll to bottom | — | ✅ `scrollIntoView` on message/stream changes |
| Stream abort on unmount | — | ✅ `AbortController` cleanup in useEffect |
| Chat CSS (300+ lines) | §9 | ✅ Glassmorphism, responsive, streaming cursor |
| Mobile responsive chat | §11 Wk4 | ✅ Breakpoints at ≤600px |

### Frontend Build
| Item | Status |
|---|---|
| `npm run build` passes | ✅ 31 modules, 0 errors |

---

## 2. Partially Implemented ⚠️

### AI Provider Deviation
The PRD specifies **OpenAI** (GPT-4o mini + text-embedding-3-small). The codebase uses **Google Gemini** via OpenAI-compatible adapter:

```properties
spring.ai.openai.base-url=https://generativelanguage.googleapis.com/v1beta/openai
spring.ai.openai.chat.options.model=gemini-2.0-flash
spring.ai.openai.embedding.options.model=text-embedding-005
```

This is a **deliberate migration** from prior conversations and is functionally equivalent. The vector dimension mismatch (1536 in PRD vs 768 from Gemini) has been resolved by the V2 migration + `@Array(length = 768)`.

> [!NOTE]
> Not a bug — a documented, intentional deviation. The PRD's embedding dimension reference (`float[1536]` in §8 Step 3) no longer matches the running system, but the system is internally consistent.

### Spring AI Version

```xml
<spring-ai.version>1.0.0-M1</spring-ai.version>
```

This is a **milestone** release. The user explicitly instructed to **not upgrade** unless there's a reproducible issue. The app currently starts successfully. The `StreamingChatModel` and `EmbeddingModel` interfaces used in `ChatService.java` and `EmbeddingService.java` are compatible with M1.

> [!NOTE]
> The M1 API uses `chatResponse.getResult().getOutput().getContent()` (line 240 of ChatService). This works, but if Spring AI is ever upgraded, this call chain will break due to the M1→GA refactor. This is **accepted technical debt**, not a current bug.

### Cloudflare R2 Storage — Partially Wired

| Aspect | Status |
|---|---|
| `StorageService` interface | ✅ Exists |
| `LocalStorageService` (dev) | ✅ With `@ConditionalOnProperty` |
| `R2StorageService` (prod) | ❌ **Class does not exist** |
| R2 config properties in `application.properties` | ✅ Defined |
| AWS S3 SDK dependency in `pom.xml` | ✅ Added (`2.25.60`) |
| `app.r2.enabled` conditional toggle | ✅ Wired on LocalStorageService |

**Verdict:** The wiring is in place — `pom.xml` has the SDK, `LocalStorageService` has the conditional, config properties exist. But the actual `R2StorageService.java` class hasn't been written. Setting `app.r2.enabled=true` right now would cause a **startup failure** (no `StorageService` bean).

---

## 3. Completely Missing ❌

### Backend
| PRD Requirement | Status |
|---|---|
| `R2StorageService` implementation | ❌ File doesn't exist |

### Project / Deployment
| PRD Requirement | PRD Ref | Status |
|---|---|---|
| Root `README.md` | §11 Wk4 | ❌ Does not exist |
| Deploy frontend to Vercel | §10 | ❌ |
| Deploy Spring Boot to Railway | §10 | ❌ |
| Deploy PostgreSQL to Neon | §10 | ❌ |
| Deploy files to Cloudflare R2 | §10 | ❌ |

### Testing
| Item | Status |
|---|---|
| Unit tests | ❌ 0 test files (`src/test/` is empty) |
| Integration tests | ❌ |

> [!IMPORTANT]
> Test dependencies (`spring-boot-starter-test`, `spring-security-test`) are in `pom.xml`, but zero tests have been written. PRD doesn't explicitly mandate tests, but RULES.md doesn't exclude them either.

---

## 4. Remaining Bugs & RULES.md Violations 🐛

### Stale Comment in `client.js` (Minor)

[client.js:106](file:///d:/ai-document-intelligence-platform/frontend/src/api/client.js#L106):
```js
// Placeholder export methods for document operations to prevent compilation issues
```
This comment is stale. The functions are real implementations, not placeholders.

### RULES.md Violation: `AuthRepository` Cross-Referenced Across Features

[DocumentService.java:3](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/document/DocumentService.java#L3) and [ChatService.java:3](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/chat/ChatService.java#L3) both `import com.docai.auth.AuthRepository` directly.

RULES.md §1: *"One folder per feature. Never mix features."*

Both services need user lookup, but doing it via `AuthRepository` creates a cross-package dependency. A cleaner approach would be a shared `UserResolver` in `com.docai.shared`. This is a **structural smell**, not a functional bug.

### `ObjectMapper` Injected but Never Used

[ChatService.java:38](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/chat/ChatService.java#L38):
```java
private final ObjectMapper objectMapper;
```
This field is injected via `@RequiredArgsConstructor` but never referenced anywhere in the class. The JSON serialization in `buildCitationsJson()` and `escapeJson()` is done manually via string concatenation. This is dead code — it should either be used or removed.

### Manual JSON String Building Instead of ObjectMapper

[ChatService.java:243-260](file:///d:/ai-document-intelligence-platform/backend/src/main/java/com/docai/chat/ChatService.java#L243-L260): `buildCitationsJson()` and `escapeJson()` build JSON via string concatenation. This is fragile — if a chunk's `pageNumber` is null, the JSON output will contain `"pageNumber":null` literally, which is valid JSON but could also produce `"pageNumber":0` depending on unboxing. The injected `ObjectMapper` could handle all edge cases correctly.

### Missing `hibernate.version` Property in `pom.xml`

[pom.xml:102](file:///d:/ai-document-intelligence-platform/backend/pom.xml#L102):
```xml
<version>${hibernate.version}</version>
```
The `hibernate.version` property is **not defined** in the `<properties>` section of the POM. This only works because Spring Boot's parent POM (`spring-boot-starter-parent:3.3.1`) defines `hibernate.version` in its own dependency management. This is fragile — it couples to an implicit parent property that could change.

> [!NOTE]
> This is inherited behavior and currently works, but it's implicit coupling worth being aware of.

---

## 5. What Changed Since Last Audit

| Item | Before | After |
|---|---|---|
| Embedding vector dimension | 1536 (wrong for Gemini) | **768** (correct) ✅ |
| `e.printStackTrace()` in production code | 2 occurrences | **0** — replaced with SLF4J ✅ |
| CORS origins | Hardcoded to localhost | **Env-var driven** via `${FRONTEND_URL}` ✅ |
| Chat frontend | 17-line placeholder | **215-line full implementation** ✅ |
| `MessageBubble.jsx` | Did not exist | **Created** (40 lines) ✅ |
| SSE `streamQuery()` in `client.js` | Missing | **Implemented** (93 lines) ✅ |
| Chat CSS | 0 lines | **300+ lines** added ✅ |
| Upload progress bar | Fake 70% | **Indeterminate animation** ✅ |
| Mobile responsive chat | None | **Breakpoints at ≤600px** ✅ |
| `LocalStorageService` | Always active | **Conditional** on `app.r2.enabled` ✅ |
| AWS S3 SDK in `pom.xml` | Not present | **Added** ✅ |
| `R2StorageService` class | Not present | **Still missing** ❌ |
| `README.md` | Not present | **Still missing** ❌ |

---

## 6. Summary Scorecard

| Area | PRD Completion | Change vs Last Audit |
|---|---|---|
| Auth (backend + frontend) | **100%** | — |
| Documents (backend) | **100%** | — |
| Documents (frontend) | **100%** | ↑ from 95% (fixed progress bar) |
| RAG Pipeline (backend) | **100%** | ↑ (fixed vector dim) |
| Chat (backend) | **100%** | — |
| Chat (frontend) | **100%** | ↑↑ from ~5% (full implementation) |
| Database schema | **100%** | — |
| Infrastructure / Config | **95%** | ↑ from 85% (fixed CORS, conditional storage) |
| R2 Storage | **60%** | ↑ from 0% (SDK + conditional wiring, no impl) |
| README | **0%** | — |
| Deployment | **0%** | — |
| Testing | **0%** | — |
| **Overall PRD Completion** | **~80%** | ↑ from ~65% |

---

## 7. What's Left to Reach 100%

| Priority | Item | Effort |
|---|---|---|
| 🟡 Medium | Write `R2StorageService.java` (implements StorageService using AWS S3 SDK) | ~1 hour |
| 🟡 Medium | Write root `README.md` (setup, env vars, deployment guide) | ~30 min |
| 🟢 Low | Remove unused `ObjectMapper` from ChatService OR use it for JSON building | 5 min |
| 🟢 Low | Fix stale "placeholder" comment in `client.js` | 1 min |
| 🟢 Low | Deploy to Vercel/Railway/Neon/R2 | Variable |
| 🟢 Low | Write unit/integration tests | Variable |

> [!IMPORTANT]
> **All three PRD screens are functionally complete.** The full user journey (register → upload → process → chat with streaming + citations) is implemented end-to-end. The remaining gaps are production infrastructure (R2, README, deployment) and code hygiene (dead code, stale comment). The application is demo-ready on localhost.
