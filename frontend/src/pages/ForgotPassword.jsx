import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import { Alert, Field } from '../components/UI';
import { authApi } from '../api';
import { getErrorMessage } from '../utils/format';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Email không hợp lệ.');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.forgotPassword({ email: email.trim() });
      setMessage(typeof res.data === 'string' ? res.data : 'Yêu cầu đặt lại mật khẩu đã được gửi.');
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể gửi yêu cầu. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Quên mật khẩu" subtitle="Nhập email đã đăng ký để nhận hướng dẫn đặt lại mật khẩu.">
      {error && <Alert tone="error">{error}</Alert>}
      {message && <Alert tone="success">{message}</Alert>}

      <form onSubmit={handleSubmit} noValidate>
        <Field label="Email đăng ký" required>
          <input
            className={`input${error ? ' invalid' : ''}`}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="email@example.com"
            autoFocus
          />
        </Field>

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? <><span className="spinner" style={{ borderTopColor: '#fff', borderColor: 'rgba(255,255,255,.35)' }} /> Đang gửi...</> : 'Gửi yêu cầu'}
        </button>
      </form>

      <div className="auth-foot">
        Nhớ mật khẩu rồi? <Link to="/login">Quay lại đăng nhập</Link>
      </div>
    </AuthShell>
  );
};

export default ForgotPassword;
