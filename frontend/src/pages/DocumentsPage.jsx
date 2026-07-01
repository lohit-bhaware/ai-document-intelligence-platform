import React, { useState, useEffect } from 'react';
import { getDocuments, uploadDocument, deleteDocument, getUser, logout } from '../api/client';
import UploadZone from '../components/UploadZone';
import DocumentCard from '../components/DocumentCard';

function DocumentsPage() {
  const [documents, setDocuments] = useState([]);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState('');
  const user = getUser();

  // Fetch document list on mount
  useEffect(() => {
    fetchDocs();
  }, []);

  // Poll for document status updates if any document is in non-terminal states (PENDING, PROCESSING)
  useEffect(() => {
    const hasPendingOrProcessing = documents.some(
      (doc) => doc.status === 'PENDING' || doc.status === 'PROCESSING'
    );

    if (!hasPendingOrProcessing) return;

    // Set up 3s polling timer
    const intervalId = setInterval(() => {
      refreshDocListSilent();
    }, 3000);

    return () => clearInterval(intervalId);
  }, [documents]);

  const fetchDocs = async () => {
    try {
      setError('');
      const response = await getDocuments();
      if (response.success && response.data) {
        setDocuments(response.data);
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch documents');
    }
  };

  const refreshDocListSilent = async () => {
    try {
      const response = await getDocuments();
      if (response.success && response.data) {
        setDocuments(response.data);
      }
    } catch (err) {
      console.error('Silent refresh failed:', err);
    }
  };

  const handleUpload = async (file) => {
    setIsUploading(true);
    setError('');
    try {
      const response = await uploadDocument(file);
      if (response.success && response.data) {
        // Prepend new document to the top of list
        setDocuments((prevDocs) => [response.data, ...prevDocs]);
      }
    } catch (err) {
      setError(err.message || 'Failed to upload document');
    } finally {
      setIsUploading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this document? This will permanently delete the document and all associated chat logs.')) {
      return;
    }

    try {
      setError('');
      await deleteDocument(id);
      setDocuments((prevDocs) => prevDocs.filter((doc) => doc.id !== id));
    } catch (err) {
      setError(err.message || 'Failed to delete document');
    }
  };

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div className="dashboard-logo">DocAI</div>
        <div className="user-info">
          <span className="user-name">Welcome, {user?.name || 'User'}</span>
          <button className="btn-logout" onClick={logout}>Sign Out</button>
        </div>
      </header>

      {error && (
        <div className="form-error" style={{ marginBottom: '1.5rem', textAlign: 'center' }}>
          {error}
        </div>
      )}

      <main className="dashboard-content">
        <aside>
          <div style={{ position: 'sticky', top: '2rem' }}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '1rem', color: '#ffffff' }}>Upload Document</h2>
            <UploadZone onUpload={handleUpload} isUploading={isUploading} />
          </div>
        </aside>

        <section>
          <h2 style={{ fontSize: '1.25rem', marginBottom: '1rem', color: '#ffffff' }}>Your Documents</h2>
          <div className="documents-grid">
            {documents.length === 0 ? (
              <div className="no-documents">
                <div className="no-documents-icon" style={{ fontSize: '3rem', marginBottom: '0.5rem' }}>📁</div>
                <p>No documents uploaded yet.</p>
                <p style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                  Upload a PDF or TXT file using the panel to start chatting.
                </p>
              </div>
            ) : (
              documents.map((doc) => (
                <DocumentCard 
                  key={doc.id} 
                  doc={doc} 
                  onDelete={handleDelete} 
                />
              ))
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

export default DocumentsPage;
