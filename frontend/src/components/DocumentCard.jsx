import React from 'react';
import { useNavigate } from 'react-router-dom';

function DocumentCard({ doc, onDelete }) {
  const navigate = useNavigate();

  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'PENDING':
        return <span className="badge badge-pending">Pending</span>;
      case 'PROCESSING':
        return <span className="badge badge-processing">Processing</span>;
      case 'READY':
        return <span className="badge badge-ready">Ready</span>;
      case 'FAILED':
        return <span className="badge badge-failed">Failed</span>;
      default:
        return <span className="badge badge-pending">{status}</span>;
    }
  };

  const handleChatClick = () => {
    if (doc.status === 'READY') {
      navigate(`/chat/${doc.id}`);
    }
  };

  return (
    <div className="doc-card">
      <div>
        <div className="doc-header">
          <h3 className="doc-title" title={doc.filename}>{doc.filename}</h3>
          {getStatusBadge(doc.status)}
        </div>
        
        <div className="doc-meta">
          <div>Size: {formatFileSize(doc.fileSize)}</div>
          {doc.status === 'READY' && <div>Chunks: {doc.chunkCount}</div>}
          {doc.status === 'FAILED' && (
            <div style={{ color: '#f87171', fontSize: '0.75rem', marginTop: '0.25rem', wordBreak: 'break-word' }}>
              Error: {doc.errorMsg || 'Parsing failed'}
            </div>
          )}
        </div>
      </div>

      <div className="doc-actions">
        <button 
          className="btn-chat" 
          onClick={handleChatClick}
          disabled={doc.status !== 'READY'}
        >
          Ask Document
        </button>
        <button 
          className="btn-delete" 
          onClick={() => onDelete(doc.id)}
          title="Delete document"
        >
          🗑️
        </button>
      </div>
    </div>
  );
}

export default DocumentCard;
