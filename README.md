# AI Document Intelligence Platform

A full-stack web application that allows users to upload documents (PDF, TXT) and chat with them using natural language. Built with a Retrieval-Augmented Generation (RAG) pipeline using Spring Boot, React, PostgreSQL/pgvector, and Google Gemini AI.

## Features

- **Document Upload & Processing:** Upload PDF or TXT files (up to 10MB).
- **RAG Pipeline:** Automatic background chunking and vector embedding via Google Gemini.
- **Smart Chat:** Ask questions about your documents and get natural language answers.
- **Streaming Responses:** Real-time token streaming with Server-Sent Events (SSE).
- **Citations:** Every answer includes exact page numbers and chunk references from the source document.
- **Secure:** JWT-based authentication and document-level authorization.

## Tech Stack

- **Frontend:** React, Vite
- **Backend:** Spring Boot (Java 21), Spring Security, Spring AI
- **Database:** PostgreSQL with `pgvector` extension
- **AI Models:** Google Gemini via OpenAI-compatible API
- **File Storage:** Local disk (Development) / Cloudflare R2 (Production)

---

## Prerequisites

- **Java 21**
- **Node.js 18+**
- **Docker & Docker Compose** (for PostgreSQL)
- **Google Gemini API Key** (or OpenAI API Key)

---

## Local Development Setup

### 1. Start the Database
The project includes a `docker-compose.yml` to spin up PostgreSQL with the `pgvector` extension.
```bash
docker compose up -d
```
*(This starts Postgres on port 5432 with user/pass: dev/dev and db: docai)*

### 2. Configure the Backend
Set your AI provider API key as an environment variable before starting the backend. By default, it expects a Gemini API key.
```bash
# On Windows (PowerShell)
$env:GEMINI_API_KEY="your-gemini-api-key"

# On Mac/Linux
export GEMINI_API_KEY="your-gemini-api-key"
```

### 3. Run the Backend
Navigate to the `backend` directory and run the Spring Boot application using the Maven wrapper:
```bash
cd backend
./mvnw spring-boot:run
```
*(The backend will start on `http://localhost:8080`. Flyway will automatically run the database migrations.)*

### 4. Run the Frontend
Navigate to the `frontend` directory, install dependencies, and start the Vite dev server:
```bash
cd frontend
npm install
npm run dev
```
*(The frontend will start on `http://localhost:5173`)*

---

## Environment Variables Reference

### Backend (`application.properties`)
These can be overridden via system environment variables:
- `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USER`, `DATABASE_PASSWORD` (default: dev settings)
- `GEMINI_API_KEY`: Required. Your Google Gemini API Key.
- `JWT_SECRET`: Required for production. Secret used to sign JWTs.
- `FRONTEND_URL`: Allowed CORS origin (default: `http://localhost:5173,http://localhost:3000`).
- `R2_ENABLED`: Set to `true` to enable Cloudflare R2 storage (default: `false`).
- `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME`: Required if R2 is enabled.

### Frontend
- `VITE_API_URL`: The backend API URL (default: `http://localhost:8080/api`).

---

## Production Deployment Guide

The platform is designed to be deployed across a free-tier compatible stack.

### 1. Database: Neon (Serverless Postgres)
- Create a Neon project.
- Run `CREATE EXTENSION IF NOT EXISTS vector;` in their SQL editor.
- Get your connection URL and replace the local `DATABASE_URL` for the backend.

### 2. File Storage: Cloudflare R2
- Create an R2 bucket (e.g., `docai-files`).
- Generate an R2 API Token with Object Read & Write permissions.
- Provide the Account ID, Access Key ID, and Secret Access Key to the backend via env vars.

### 3. Backend: Railway
- Connect your GitHub repo to Railway.
- Deploy the `backend/` directory. Railway will automatically detect the `Procfile`.
- Add all required environment variables (`DATABASE_URL`, `GEMINI_API_KEY`, `JWT_SECRET`, `R2_*`).

### 4. Frontend: Vercel
- Import the repository in Vercel.
- Set the Root Directory to `frontend`.
- Vercel will automatically detect Vite and the `vercel.json` file.
- Add `VITE_API_URL` pointing to your Railway backend URL.

---

## Project Architecture

- **`backend/src/main/java/com/docai/auth/`**: Registration, login, and JWT logic.
- **`backend/src/main/java/com/docai/document/`**: Document uploading, deletion, and storage switching (Local vs R2).
- **`backend/src/main/java/com/docai/rag/`**: The core Retrieval-Augmented Generation pipeline (parsing PDF/TXT, chunking, embedding, vector storage, and similarity search).
- **`backend/src/main/java/com/docai/chat/`**: Conversation history and the SSE streaming endpoint for chatting with documents.
- **`frontend/src/pages/`**: React pages (Login, Documents List, Chat UI).
- **`frontend/src/api/`**: Centralized API client (`client.js`) handling all HTTP requests and SSE streams.
