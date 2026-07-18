-- Fix vector dimension: gemini-embedding-001 outputs 3072 dimensions.
-- The original V1 migration created embedding as vector(1536) based on the OpenAI PRD spec,
-- but the project uses Gemini which requires 3072 dims via the OpenAI-compatible endpoint.

-- Drop the existing ivfflat index first
DROP INDEX IF EXISTS document_chunks_embedding_idx;

-- Alter the column type to vector(3072)
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(3072);

-- Recreate the cosine similarity index
CREATE INDEX document_chunks_embedding_idx ON document_chunks
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 50);
