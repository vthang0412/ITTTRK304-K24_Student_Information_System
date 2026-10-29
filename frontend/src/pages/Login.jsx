import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import { Alert, Field } from '../components/UI';
import { EyeIcon, EyeOffIcon, LockIcon, MailIcon } from '../components/Icons';
import { authApi } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { getErrorMessage } from '../utils/format';

const DEMO = [
  { role: 'Quản trị viên', username: 'admin', password: '123456' },
  { role: 'Giảng viên', username: 'lecturer', password: '123456' },
  { role: 'Sinh viên', username: 'student', password: '123456' },
];

const Login = () => {
  const [form, setForm] = useState({ username: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [skipAutoRedirect, setSkipAutoRedirect] = useState(false);
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => {
    if (isAuthenticated && !skipAutoRedirect) navigate('/dashboard', { replace: true });
  }, [isAuthenticated, navigate, skipAutoRedirect]);

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  /* Kiểm tra xem lỗi có phải do tài khoản bị vô hiệu hoá không */
  const isDisabledAccount = (err) => {
    const data = err?.response?.data;
    const msg = (typeof data === 'string' ? data : data?.message) || '';
    return msg === 'Account is disabled' || msg.toLowerCase().includes('disabled');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!form.username.trim() || !form.password) {
      setError('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.');
      toast.showAuthModal({
        icon: 'error',
        title: 'Thông tin đăng nhập chưa hợp lệ',
        message: 'Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.',
        duration: 2200,
      });
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.login({ username: form.username.trim(), password: form.password });
      setSkipAutoRedirect(true);
      login(res.data);
      toast.showAuthModal({
        icon: 'success',
        title: 'Đăng nhập thành công',
        message: 'Bạn sẽ được chuyển hướng đến trang tổng quan.',
        duration: 2000,
      });
      window.setTimeout(() => {
        navigate('/dashboard', { replace: true });
      }, 2200);
    } catch (err) {
      if (isDisabledAccount(err)) {
        toast.showAuthModal({
          icon: 'warning',
          title: 'Tài khoản đã bị khóa',
          message: 'Tài khoản của bạn đã bị quản trị viên ngừng hoạt động. Vui lòng liên hệ Admin để được hỗ trợ.',
          duration: 4000,
          showConfirmButton: true,
          confirmButtonText: 'Đã hiểu',
        });
      } else {
        const message = getErrorMessage(err, 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.');
        setError(message);
        toast.showAuthModal({
          icon: 'error',
          title: 'Đăng nhập thất bại',
          message,
          duration: 2200,
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (account) => {
    setForm({ username: account.username, password: account.password });
    setError('');
  };

  return (
    <AuthShell title="Đăng nhập" subtitle="Chào mừng bạn quay lại hệ thống quản lý đào tạo.">
      {error && <Alert tone="error">{error}</Alert>}

      <form onSubmit={handleSubmit} noValidate>
        <Field label="Tên đăng nhập" required>
          <div style={{ position: 'relative' }}>
            <MailIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
            <input
              className={`input${error ? ' invalid' : ''}`}
              style={{ paddingLeft: 38 }}
              name="username"
              value={form.username}
              onChange={handleChange}
              placeholder="Nhập tên đăng nhập"
              autoComplete="username"
              autoFocus
            />
          </div>
        </Field>

        <Field label="Mật khẩu" required>
          <div style={{ position: 'relative' }}>
            <LockIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
            <input
              className="input"
              style={{ paddingLeft: 38, paddingRight: 42 }}
              name="password"
              type={showPassword ? 'text' : 'password'}
              value={form.password}
              onChange={handleChange}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              style={{ position: 'absolute', right: 6, top: 5, border: 'none', background: 'none', color: 'var(--muted)', padding: 6 }}
            >
              {showPassword ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
            </button>
          </div>
        </Field>

        <div className="auth-extra">
          <span className="muted">Bảo mật bằng JWT</span>
          <Link to="/forgot-password">Quên mật khẩu?</Link>
        </div>

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? <><span className="spinner" style={{ borderTopColor: '#fff', borderColor: 'rgba(255,255,255,.35)' }} /> Đang đăng nhập...</> : 'Đăng nhập'}
        </button>
      </form>

      <div style={{ marginTop: 18, padding: 12, border: '1px dashed var(--border-strong)', borderRadius: 10, background: 'var(--surface-2)' }}>
        <div className="small" style={{ fontWeight: 600, marginBottom: 8 }}>Tài khoản demo</div>
        <div style={{ display: 'grid', gap: 6 }}>
          {DEMO.map((d) => (
            <button
              key={d.username}
              type="button"
              onClick={() => fillDemo(d)}
              className="flex-between"
              style={{ border: 'none', background: 'none', padding: '2px 0', textAlign: 'left' }}
            >
              <span className="small muted">{d.role}</span>
              <span className="chip" style={{ fontSize: 11 }}>{d.username} / {d.password}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="auth-foot">
        Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
      </div>
    </AuthShell>
  );
};

export default Login;
