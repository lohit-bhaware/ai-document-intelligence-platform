import React, { useState, useRef } from 'react';

function UploadZone({ onUpload, isUploading }) {
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!isUploading) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    
    if (isUploading) return;

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      processFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      processFile(e.target.files[0]);
    }
  };

  const processFile = (file) => {
    setError('');
    
    const filename = file.name.toLowerCase();
    if (!filename.endsWith('.pdf') && !filename.endsWith('.txt')) {
      setError('Only PDF and TXT files are supported');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setError('File size must be under 10MB');
      return;
    }

    onUpload(file);
  };

  const handleClick = () => {
    if (!isUploading && fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', width: '100%' }}>
      <div
        className={`upload-zone ${isDragging ? 'dragging' : ''}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={handleClick}
      >
        <input
          type="file"
          ref={fileInputRef}
          onChange={handleFileChange}
          accept=".pdf,.txt"
          style={{ display: 'none' }}
          disabled={isUploading}
        />
        
        <div className="upload-icon" style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>
          {isUploading ? '🔄' : '📤'}
        </div>
        
        <p className="upload-text">
          {isUploading ? 'Uploading file...' : 'Drag & drop a file here'}
        </p>
        <p className="upload-subtext">
          {isUploading ? 'Please wait while file is saved' : 'or click to browse files'}
        </p>
        
        {!isUploading && (
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.75rem' }}>
            Supports PDF, TXT up to 10MB
          </span>
        )}

        {isUploading && (
          <div className="upload-progress-container">
            <div className="upload-progress-bar">
              <div className="upload-progress-fill upload-progress-indeterminate"></div>
            </div>
          </div>
        )}
      </div>
      
      {error && (
        <div className="form-error" style={{ textAlign: 'center' }}>
          {error}
        </div>
      )}
    </div>
  );
}

export default UploadZone;
