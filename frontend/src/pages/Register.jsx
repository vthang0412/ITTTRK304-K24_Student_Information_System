import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import { Alert, Field } from '../components/UI';
import { authApi } from '../api';
import { useAuth } from '../context/AuthContext';
import { getErrorMessage } from '../utils/format';

const initial = {
  fullName: '', username: '', email: '', role: 'STUDENT', password: '', confirmPassword: '',
};

const Register = () => {
  const [form, setForm] = useState(initial);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard', { replace: true });
  }, [isAuthenticated, navigate]);

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const validate = () => {
    if (form.fullName.trim().length < 2) return 'Họ và tên phải có ít nhất 2 ký tự.';
    if (form.username.trim().length < 3) return 'Tên đăng nhập phải có ít nhất 3 ký tự.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Email không hợp lệ.';
    if (form.password.length < 6) return 'Mật khẩu phải có ít nhất 6 ký tự.';
    if (form.password !== form.confirmPassword) return 'Mật khẩu xác nhận không khớp.';
    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const validation = validate();
    if (validation) { setError(validation); return; }

    setLoading(true);
    try {
      const res = await authApi.register({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      login(res.data);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, 'Đăng ký thất bại. Vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Tạo tài khoản" subtitle="Đăng ký để truy cập không gian học vụ của bạn.">
      {error && <Alert tone="error">{error}</Alert>}
      <form onSubmit={handleSubmit} noValidate>
        <Field label="Họ và tên" required>
          <input className="input" name="fullName" value={form.fullName} onChange={handleChange} placeholder="Nguyễn Văn A" />
        </Field>

        <div className="form-grid">
          <Field label="Tên đăng nhập" required>
            <input className="input" name="username" value={form.username} onChange={handleChange} placeholder="nguyenvana" autoComplete="username" />
          </Field>
          <Field label="Vai trò" required>
            <select className="select" name="role" value={form.role} onChange={handleChange}>
              <option value="STUDENT">Sinh viên</option>
              <option value="LECTURER">Giảng viên</option>
            </select>
          </Field>
        </div>

        <Field label="Email" required>
          <input className="input" type="email" name="email" value={form.email} onChange={handleChange} placeholder="email@example.com" autoComplete="email" />
        </Field>

        <div className="form-grid">
          <Field label="Mật khẩu" required hint="Tối thiểu 6 ký tự">
            <input className="input" type="password" name="password" value={form.password} onChange={handleChange} autoComplete="new-password" />
          </Field>
          <Field label="Xác nhận mật khẩu" required>
            <input className="input" type="password" name="confirmPassword" value={form.confirmPassword} onChange={handleChange} autoComplete="new-password" />
          </Field>
        </div>

        <button className="btn btn-primary btn-block" type="submit" disabled={loading} style={{ marginTop: 4 }}>
          {loading ? <><span className="spinner" style={{ borderTopColor: '#fff', borderColor: 'rgba(255,255,255,.35)' }} /> Đang tạo tài khoản...</> : 'Đăng ký'}
        </button>
      </form>

      <div className="auth-foot">
        Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
      </div>
    </AuthShell>
  );
};

export default Register;
