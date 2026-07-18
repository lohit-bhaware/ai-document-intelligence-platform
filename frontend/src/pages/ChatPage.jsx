import React, { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getChatHistory, clearChatHistory, streamQuery } from '../api/client';
import MessageBubble from '../components/MessageBubble';

function ChatPage() {
  const { docId } = useParams();
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingContent, setStreamingContent] = useState('');
  const [streamingCitations, setStreamingCitations] = useState([]);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const messagesEndRef = useRef(null);
  const abortRef = useRef(null);

  // Load chat history on mount
  useEffect(() => {
    loadHistory();
    return () => {
      // Cancel any active stream on unmount
      if (abortRef.current) abortRef.current();
    };
  }, [docId]);

  // Auto-scroll to bottom when messages change or during streaming
  useEffect(() => {
    scrollToBottom();
  }, [messages, streamingContent]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const loadHistory = async () => {
    try {
      setIsLoading(true);
      setError('');
      const response = await getChatHistory(docId);
      if (response.success && response.data) {
        setMessages(response.data.messages || []);
      }
    } catch (err) {
      setError(err.message || 'Failed to load chat history');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSend = () => {
    const question = input.trim();
    if (!question || isStreaming) return;

    setInput('');
    setError('');
    setIsStreaming(true);
    setStreamingContent('');
    setStreamingCitations([]);

    // Add user message immediately for instant feedback
    const userMessage = {
      id: `temp-user-${Date.now()}`,
      role: 'user',
      content: question,
      citations: '[]',
    };
    setMessages((prev) => [...prev, userMessage]);

    // Start SSE stream
    const abort = streamQuery(docId, question, {
      onToken: (token) => {
        setStreamingContent((prev) => prev + token);
      },
      onCitations: (citations) => {
        setStreamingCitations(citations);
      },
      onDone: (messageId) => {
        // Finalize: add assistant message to the list and clear streaming state
        setStreamingContent((prevContent) => {
          setStreamingCitations((prevCitations) => {
            const assistantMessage = {
              id: messageId,
              role: 'assistant',
              content: prevContent,
              citations: prevCitations,
            };
            setMessages((prev) => [...prev, assistantMessage]);
            return [];
          });
          return '';
        });
        setIsStreaming(false);
        abortRef.current = null;
      },
      onError: (errorMsg) => {
        setError(errorMsg);
        setIsStreaming(false);
        setStreamingContent('');
        setStreamingCitations([]);
        abortRef.current = null;
      },
    });

    abortRef.current = abort;
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleClearHistory = async () => {
    if (!window.confirm('Clear all chat history for this document?')) return;

    try {
      setError('');
      await clearChatHistory(docId);
      setMessages([]);
    } catch (err) {
      setError(err.message || 'Failed to clear history');
    }
  };

  return (
    <div className="chat-container">
      {/* Header */}
      <header className="chat-header">
        <Link to="/documents" className="back-button" title="Back to documents">
          ← Back
        </Link>
        <div className="chat-header-title">
          <h1>Document Chat</h1>
        </div>
        <button
          className="btn-clear-history"
          onClick={handleClearHistory}
          disabled={isStreaming || messages.length === 0}
        >
          Clear History
        </button>
      </header>

      {/* Error Banner */}
      {error && (
        <div className="chat-error">
          {error}
          <button className="chat-error-dismiss" onClick={() => setError('')}>✕</button>
        </div>
      )}

      {/* Message Thread */}
      <div className="chat-messages">
        {isLoading ? (
          <div className="chat-empty-state">Loading conversation…</div>
        ) : messages.length === 0 && !isStreaming ? (
          <div className="chat-empty-state">
            <div className="chat-empty-icon">💬</div>
            <p>No messages yet.</p>
            <p className="chat-empty-hint">Ask a question about your document to get started.</p>
          </div>
        ) : (
          <>
            {messages.map((msg) => (
              <MessageBubble
                key={msg.id}
                role={msg.role}
                content={msg.content}
                citations={msg.citations}
                isStreaming={false}
              />
            ))}

            {/* Show streaming assistant message */}
            {isStreaming && streamingContent && (
              <MessageBubble
                role="assistant"
                content={streamingContent}
                citations={streamingCitations}
                isStreaming={true}
              />
            )}
          </>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input Bar */}
      <div className="chat-input-bar">
        <textarea
          className="chat-input"
          placeholder="Ask a question about the document…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isStreaming}
          rows={1}
        />
        <button
          className="btn-send"
          onClick={handleSend}
          disabled={isStreaming || !input.trim()}
          title="Send message"
        >
          {isStreaming ? '⏳' : '➤'}
        </button>
      </div>
    </div>
  );
}

export default ChatPage;
