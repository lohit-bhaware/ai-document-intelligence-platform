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

// Placeholder export methods for chat operations
export async function getChatHistory(docId) {
  return request(`/chat/${docId}/history`);
}

export async function clearChatHistory(docId) {
  return request(`/chat/${docId}/history`, {
    method: 'DELETE',
  });
}
