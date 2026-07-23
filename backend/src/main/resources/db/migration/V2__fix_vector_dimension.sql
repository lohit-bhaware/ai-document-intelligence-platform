-- Fix vector dimension: gemini-embedding-001 outputs 3072 dimensions.
-- The original V1 migration created embedding as vector(1536) based on the OpenAI PRD spec,
-- but the project uses Gemini which requires 3072 dims via the OpenAI-compatible endpoint.
--
-- NOTE: pgvector's ivfflat and hnsw indexes both cap out at 2000 dimensions (hard limit),
-- so a 3072-dim column cannot be ANN-indexed. At this project's scale, an exact sequential
-- scan for cosine distance is fast enough and is actually more accurate than an approximate
-- index anyway, so we intentionally do not create an index on this column.

-- Drop the existing ivfflat index (created in V1 for the old 1536-dim column)
DROP INDEX IF EXISTS document_chunks_embedding_idx;

-- Alter the column type to vector(3072)
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(3072);
