const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export function getToken() {
  return localStorage.getItem('token');
}

export function setToken(token) {
  if (token) {
    localStorage.setItem('token', token);
  } else {
    localStorage.removeItem('token');
  }
}

export function getUser() {
  const user = localStorage.getItem('user');
  return user ? JSON.parse(user) : null;
}

export function setUser(user) {
  if (user) {
    localStorage.setItem('user', JSON.stringify(user));
  } else {
    localStorage.removeItem('user');
  }
}

export function logout() {
  setToken(null);
  setUser(null);
  window.location.href = '/login';
}

async function request(endpoint, options = {}) {
  const token = getToken();
  
  const headers = {
    ...options.headers,
  };

  // Only set application/json if we are not sending multipart data (like document upload)
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const config = {
    ...options,
    headers,
  };

  const response = await fetch(`${API_URL}${endpoint}`, config);

  if (response.status === 401) {
    setToken(null);
    setUser(null);
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new Error('Unauthorized');
  }

  // SSE response handling for specific endpoints
  if (response.headers.get('Content-Type')?.includes('text/event-stream')) {
    return response; // Return raw response for SSE connection setup
  }

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || 'Something went wrong');
  }

  return data;
}

export async function login(email, password) {
  const responseData = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  
  if (responseData.success && responseData.data) {
    setToken(responseData.data.token);
    setUser(responseData.data.user);
  }
  return responseData;
}

export async function register(email, password, name) {
  const responseData = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, name }),
  });
  
  if (responseData.success && responseData.data) {
    setToken(responseData.data.token);
    setUser(responseData.data.user);
  }
  return responseData;
}

// Placeholder export methods for document operations to prevent compilation issues
export async function getDocuments() {
  return request('/documents');
}

export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append('file', file);
  return request('/documents/upload', {
    method: 'POST',
    body: formData,
  });
}

export async function getDocument(id) {
  return request(`/documents/${id}`);
}

export async function deleteDocument(id) {
  return request(`/documents/${id}`, {
    method: 'DELETE',
  });
}

// Chat operations
export async function getChatHistory(docId) {
  return request(`/chat/${docId}/history`);
}

export async function clearChatHistory(docId) {
  return request(`/chat/${docId}/history`, {
    method: 'DELETE',
  });
}

/**
 * Stream a chat query via SSE (POST endpoint with JSON body).
 * Native EventSource only supports GET, so we use fetch + ReadableStream.
 *
 * @param {string} docId - Document UUID
 * @param {string} message - User question
 * @param {Object} callbacks - { onToken, onCitations, onDone, onError }
 * @returns {function} abort - Call to cancel the stream
 */
export function streamQuery(docId, message, { onToken, onCitations, onDone, onError }) {
  const controller = new AbortController();
  const token = getToken();

  fetch(`${API_URL}/chat/${docId}/query`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (response.status === 401) {
        setToken(null);
        setUser(null);
        window.location.href = '/login';
        return;
      }
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        onError(errorData.error || `Server error: ${response.status}`);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        // Keep the last potentially incomplete line in the buffer
        buffer = lines.pop() || '';

        let currentEvent = '';
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataStr = line.substring(5).trim();
            if (!dataStr) continue;

            try {
              const data = JSON.parse(dataStr);

              switch (currentEvent) {
                case 'token':
                  if (data.token !== undefined) onToken(data.token);
                  break;
                case 'citations':
                  if (data.citations) onCitations(data.citations);
                  break;
                case 'done':
                  onDone(data.messageId);
                  break;
                case 'error':
                  onError(data.message || 'Streaming failed');
                  break;
                default:
                  break;
              }
            } catch {
              // Skip malformed JSON lines
            }
            currentEvent = '';
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message || 'Connection failed');
      }
    });

  // Return an abort function the caller can use to cancel the stream
  return () => controller.abort();
}
