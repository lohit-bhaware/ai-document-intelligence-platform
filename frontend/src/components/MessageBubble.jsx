import React from 'react';

function MessageBubble({ role, content, citations, isStreaming }) {
  const isUser = role === 'user';

  const parsedCitations = React.useMemo(() => {
    if (!citations) return [];
    if (Array.isArray(citations)) return citations;
    try {
      return JSON.parse(citations);
    } catch {
      return [];
    }
  }, [citations]);

  return (
    <div className={`message-row ${isUser ? 'message-row-user' : 'message-row-assistant'}`}>
      <div className={`message-bubble ${isUser ? 'bubble-user' : 'bubble-assistant'}`}>
        <div className="message-content">
          {content}
          {isStreaming && <span className="streaming-cursor">▋</span>}
        </div>

        {!isUser && !isStreaming && parsedCitations.length > 0 && (
          <div className="message-citations">
            <span className="citations-label">Sources:</span>
            {parsedCitations.map((c, i) => (
              <span key={i} className="citation-tag">
                Chunk {c.chunkIndex}{c.pageNumber != null && `, p.${c.pageNumber}`}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default MessageBubble;
