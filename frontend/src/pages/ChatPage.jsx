import React from 'react';
import { useParams, Link } from 'react-router-dom';

function ChatPage() {
  const { docId } = useParams();
  
  return (
    <div style={{ padding: '2rem' }}>
      <h1>Chat Page Placeholder</h1>
      <p>Chatting with document ID: {docId}</p>
      <Link to="/documents">Back to Documents</Link>
    </div>
  );
}

export default ChatPage;
