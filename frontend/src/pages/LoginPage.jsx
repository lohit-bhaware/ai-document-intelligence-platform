import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { login, register, getToken } from '../api/client';

function LoginPage() {
  const [isRegistering, setIsRegistering] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    // If user is already authenticated, redirect to /documents immediately
    if (getToken()) {
      navigate('/documents');
    }
  }, [navigate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      if (isRegistering) {
        const response = await register(email, password, name);
        if (response.success) {
          navigate('/documents');
        }
      } else {
        const response = await login(email, password);
        if (response.success) {
          navigate('/documents');
        }
      }
    } catch (err) {
      setError(err.message || 'An error occurred during authentication');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="main-content">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">DocAI</h1>
          <p className="auth-subtitle">
            {isRegistering ? 'Create an account to get started' : 'Sign in to access your documents'}
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          {error && (
            <div className="form-error" style={{ marginBottom: '1.25rem', textAlign: 'center' }}>
              {error}
            </div>
          )}

          {isRegistering && (
            <div className="form-group">
              <label className="form-label" htmlFor="name">Full Name</label>
              <input
                id="name"
                className="form-input"
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="John Doe"
                required
              />
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="email">Email Address</label>
            <input
              id="email"
              className="form-input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">Password</label>
            <input
              id="password"
              className="form-input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
            />
          </div>

          <button className="btn-primary" type="submit" disabled={isLoading}>
            {isLoading ? 'Processing...' : isRegistering ? 'Register' : 'Sign In'}
          </button>
        </form>

        <div className="auth-toggle">
          {isRegistering ? 'Already have an account?' : "Don't have an account?"}
          <span 
            className="auth-toggle-link" 
            onClick={() => {
              setIsRegistering(!isRegistering);
              setError('');
            }}
          >
            {isRegistering ? 'Sign In' : 'Register'}
          </span>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
