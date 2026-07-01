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

-- Index for fast cosine similarity vector search
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx ON document_chunks
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
