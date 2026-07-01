# PRD — AI Document Q&A Platform

**Version:** 4.0  
**Author:** [Your Name]  
**Last Updated:** June 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Problem Statement](#2-problem-statement)
3. [Tech Stack](#3-tech-stack)
4. [System Architecture](#4-system-architecture)
5. [Features](#5-features)
6. [Database Schema](#6-database-schema)
7. [REST API](#7-rest-api)
8. [RAG Pipeline](#8-rag-pipeline)
9. [Frontend Screens](#9-frontend-screens)
10. [Deployment](#10-deployment)
11. [Development Phases](#11-development-phases)
12. [Out of Scope](#12-out-of-scope)

---

## 1. Overview

A web app where a user uploads documents (PDF, TXT) and chats with them using natural language. The backend runs a RAG pipeline — documents are chunked, embedded, and stored. When the user asks a question, the most relevant chunks are retrieved and sent to an LLM, which streams the answer back to the browser.

---

## 2. Problem Statement

People have documents — contracts, notes, research papers — but no fast way to query them. Ctrl+F finds words, not answers. This app lets you upload a document and ask it anything.

---

## 3. Tech Stack

**You specified:**

| Layer | Technology |
|---|---|
| Frontend | React.js |
| Backend | Spring Boot (Java) |
| API style | REST |
| AI | RAG pipeline |

**Required additions** (impossible to build without these):

| What | Choice | Why it's required |
|---|---|---|
| Database | PostgreSQL + pgvector | Need somewhere to store users, documents, messages, and vector embeddings |
| LLM + Embeddings | OpenAI API | The AI that powers the RAG — answers questions and generates embeddings |
| File storage | Local disk (dev) → Cloudflare R2 (prod) | Uploaded files have to go somewhere |
| Auth | JWT (Spring Security) | Need to know who owns which documents |
| Local dev DB | Docker (Postgres only) | Avoids installing Postgres globally |

That's it. No extra frameworks, no extra services.

---

## 4. System Architecture

```
┌─────────────────────────────────┐
│         React.js (Browser)      │
│                                 │
│  Upload page  │  Chat page      │
└────────────┬────────────────────┘
             │  REST API + SSE
             ▼
┌─────────────────────────────────┐
│       Spring Boot (Java)        │
│                                 │
│  AuthController                 │
│  DocumentController             │
│  ChatController                 │
│       │                         │
│  RAG Pipeline (service layer)   │
│    - parse document             │
│    - chunk text                 │
│    - embed via OpenAI           │
│    - vector search              │
│    - prompt + stream answer     │
└───────┬─────────────────────────┘
        │
        ▼
┌─────────────────────────────────┐
│  PostgreSQL + pgvector          │
│                                 │
│  users, documents, messages,    │
│  document_chunks (vectors)      │
└─────────────────────────────────┘
        +
┌─────────────────────────────────┐
│  Cloudflare R2                  │
│  (raw uploaded files)           │
└─────────────────────────────────┘
```

### Upload Flow

```
1. User uploads file in React
2. POST /api/documents/upload
3. Spring Boot saves file to R2, creates DB record (status: PENDING)
4. Background thread (@Async):
   a. Parse text from file
   b. Split into chunks
   c. Embed each chunk via OpenAI
   d. Store chunks + vectors in PostgreSQL
   e. Update status → READY
5. React polls GET /api/documents/:id every 3s until READY
```

### Query Flow

```
1. User types a question in React
2. POST /api/chat/:docId/query
3. Spring Boot embeds the question via OpenAI
4. pgvector finds the 5 most similar chunks
5. Spring Boot builds a prompt: system + chunks + chat history + question
6. Calls OpenAI GPT with streaming
7. Streams tokens back to React as SSE events
8. React renders tokens as they arrive
9. Full answer + source citations saved to DB
```

---

## 5. Features

### Authentication
- Register with email + password
- Login returns a JWT
- All document and chat routes require a valid JWT

### Documents
- Upload a PDF or TXT file (max 10 MB)
- Processing happens in the background; status shown in UI (PENDING → PROCESSING → READY → FAILED)
- Delete a document (removes file, vectors, and chat history)
- List all uploaded documents

### Chat
- Each document has one conversation
- User sends a question, answer streams back word by word
- Each answer shows which part of the document it came from (page/chunk)
- Last 4 messages included as context for follow-up questions
- Clear chat history

---

## 6. Database Schema

```sql
-- Enable vector support
CREATE EXTENSION IF NOT EXISTS vector;

-- Users
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  name          TEXT NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT now()
);

-- Uploaded documents
CREATE TABLE documents (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  filename    TEXT NOT NULL,
  file_key    TEXT NOT NULL,
  file_size   BIGINT NOT NULL,
  mime_type   TEXT NOT NULL,
  chunk_count INT DEFAULT 0,
  status      TEXT NOT NULL DEFAULT 'PENDING',
  error_msg   TEXT,
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- Text chunks + their vector embeddings
CREATE TABLE document_chunks (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  content     TEXT NOT NULL,
  embedding   vector(1536),
  chunk_index INT NOT NULL,
  page_number INT
);

CREATE INDEX ON document_chunks
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 50);

-- Conversations (one per document)
CREATE TABLE conversations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ DEFAULT now(),
  UNIQUE (document_id, user_id)
);

-- Chat messages
CREATE TABLE messages (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role            TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
  content         TEXT NOT NULL,
  citations       JSONB DEFAULT '[]',
  created_at      TIMESTAMPTZ DEFAULT now()
);
```

---

## 7. REST API

Base URL: `http://localhost:8080/api` (dev) / `https://your-api.railway.app/api` (prod)

All routes except `/auth/**` require: `Authorization: Bearer <jwt>`

### Auth

| Method | Endpoint | Body | Returns |
|---|---|---|---|
| POST | `/auth/register` | `{ email, password, name }` | `{ token, user }` |
| POST | `/auth/login` | `{ email, password }` | `{ token, user }` |

### Documents

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/documents` | List all documents for logged-in user |
| POST | `/documents/upload` | `multipart/form-data` with the file |
| GET | `/documents/:id` | Single document + status |
| DELETE | `/documents/:id` | Deletes file, chunks, conversation |

### Chat

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/chat/:docId/history` | Returns conversation + all messages |
| POST | `/chat/:docId/query` | Body: `{ message }` → SSE stream |
| DELETE | `/chat/:docId/history` | Clears messages |

### SSE Stream (POST `/chat/:docId/query`)

Response content type: `text/event-stream`

```
event: token
data: {"token": "The "}

event: token
data: {"token": "clause "}

event: citations
data: {"citations": [{"chunkIndex": 3, "pageNumber": 7}]}

event: done
data: {"messageId": "abc123"}

event: error
data: {"message": "Something went wrong"}
```

React listens with the browser's built-in `EventSource`.

---

## 8. RAG Pipeline

### Step 1 — Parse
- PDF → Apache PDFBox → extract text page by page
- TXT → read as plain string

### Step 2 — Chunk
- Split text into chunks of ~800 tokens
- 100-token overlap between chunks so sentences at boundaries aren't lost
- Store the page number each chunk came from

### Step 3 — Embed
- Send chunks to OpenAI `text-embedding-3-small`
- Each chunk becomes a `float[1536]` vector
- Batch 50 chunks per API call

### Step 4 — Store
- Insert all chunks + vectors into `document_chunks` table (pgvector)

### Step 5 — Search (at query time)
- Embed the user's question with the same model
- Run cosine similarity search in pgvector: top 5 closest chunks

### Step 6 — Prompt
```
System:
  You are a helpful assistant. Answer only using the context below.
  If the answer is not in the context, say so. Cite chunk numbers.

Context:
  [Chunk 3, page 7]: "..."
  [Chunk 5, page 9]: "..."
  (top 5 chunks)

Last 4 messages:
  User: "..."
  Assistant: "..."

User: "<current question>"
```

### Step 7 — Stream
- Call OpenAI GPT-4o mini with `stream: true` via Spring AI
- Pipe each token as an SSE `token` event to the browser
- On completion, save the full message + citations to DB

---

## 9. Frontend Screens

### Screen 1 — Login / Register
- Email + password form
- On success: save JWT, go to document list

### Screen 2 — Document List
- Shows all uploaded documents with status
- Upload button → file picker
- Each document: filename, status badge, "Chat" button, "Delete" button
- Status auto-refreshes until READY

### Screen 3 — Chat
- Back button to document list
- Message thread (user right, assistant left)
- Assistant messages show source citations below them
- Answer streams in word by word
- Text input + Send button at the bottom
- "Clear history" link

---

## 10. Deployment

| What | Where | Cost |
|---|---|---|
| React frontend | Vercel | Free |
| Spring Boot | Railway | Free tier |
| PostgreSQL + pgvector | Neon | Free tier |
| File storage | Cloudflare R2 | Free tier |
| **Total** | | **$0** |

**Local dev** — one `docker-compose.yml` to run Postgres with pgvector:

```yaml
version: "3.9"
services:
  postgres:
    image: pgvector/pgvector:pg16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: docai
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    volumes: ["pgdata:/var/lib/postgresql/data"]
volumes:
  pgdata:
```

Run Postgres → `./mvnw spring-boot:run` → `npm start`

**Environment variables (Spring Boot):**
```
DATABASE_URL=jdbc:postgresql://...
OPENAI_API_KEY=sk-...
R2_ACCOUNT_ID=...
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET_NAME=docai-files
JWT_SECRET=...
FRONTEND_URL=https://your-app.vercel.app
```

**Environment variables (React):**
```
REACT_APP_API_URL=https://your-api.railway.app/api
```

---

## 11. Development Phases

### Week 1 — Auth + Project Setup
- [ ] Spring Boot project with Spring Security + JWT
- [ ] `POST /auth/register` and `POST /auth/login`
- [ ] PostgreSQL connection + all tables created
- [ ] React app with Login and Register screens
- [ ] JWT saved and sent with every request

### Week 2 — Document Upload + RAG Ingestion
- [ ] `POST /documents/upload` → save to R2 → create DB record
- [ ] `@Async` background processing: parse → chunk → embed → store in pgvector
- [ ] `GET /documents` and `GET /documents/:id` (with status)
- [ ] `DELETE /documents/:id`
- [ ] React: Document List screen, upload, status polling

### Week 3 — Chat + RAG Query
- [ ] `POST /chat/:docId/query`: embed question → pgvector search → build prompt → stream via SSE
- [ ] `GET /chat/:docId/history` and `DELETE /chat/:docId/history`
- [ ] React: Chat screen, SSE streaming, citations shown

### Week 4 — Polish + Deploy
- [ ] Error states in UI (FAILED status, upload errors)
- [ ] Mobile-friendly layout
- [ ] Deploy to Vercel + Railway + Neon + R2
- [ ] README with setup instructions and live demo link

---

## 12. Out of Scope

| Feature | Reason |
|---|---|
| Multiple users per document / teams | Not mentioned, adds complexity |
| DOCX support | PDF + TXT is enough for a demo |
| Rate limiting / caching | Not mentioned — skip for now |
| OAuth / Google login | Not mentioned |
| Docker in production | Managed platforms handle this |
| TypeScript | Not mentioned |
| Tailwind / any specific CSS framework | Not mentioned — use plain CSS or whatever you're comfortable with |

---

*Version 4.0 — Strictly limited to React + Spring Boot + REST API + RAG. Only added what is physically required to build the project.*
