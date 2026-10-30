import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import { Alert, Field } from '../components/UI';
import { authApi } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { getErrorMessage } from '../utils/format';

const initial = {
  fullName: '', username: '', email: '', role: 'STUDENT', password: '', confirmPassword: '',
};

const Register = () => {
  const [form, setForm] = useState(initial);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [skipAutoRedirect, setSkipAutoRedirect] = useState(false);
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => {
    if (!isAuthenticated) {
      toast.showAuthModal({
        icon: 'info',
        title: 'Đăng ký công khai đã tắt',
        message: 'Chỉ quản trị viên mới có quyền tạo tài khoản cho sinh viên và giảng viên.',
        duration: 2200,
        showConfirmButton: true,
        confirmButtonText: 'Về trang đăng nhập',
      });
      window.setTimeout(() => navigate('/login', { replace: true }), 2200);
      return;
    }

    if (isAuthenticated && !skipAutoRedirect) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate, skipAutoRedirect, toast]);

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
    if (validation) {
      setError(validation);
      toast.showAuthModal({
        icon: 'error',
        title: 'Đăng ký thất bại',
        message: validation,
        duration: 2200,
      });
      return;
    }

    setLoading(true);
    try {
      const res = await authApi.register({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      setSkipAutoRedirect(true);
      login(res.data);
      toast.showAuthModal({
        icon: 'success',
        title: 'Đăng ký thành công',
        message: 'Tài khoản của bạn đã được tạo thành công. Đang chuyển hướng...',
        duration: 2000,
      });
      window.setTimeout(() => {
        navigate('/dashboard', { replace: true });
      }, 2200);
    } catch (err) {
      const message = getErrorMessage(err, 'Đăng ký thất bại. Vui lòng thử lại.');
      setError(message);
      toast.showAuthModal({
        icon: 'error',
        title: 'Đăng ký thất bại',
        message,
        duration: 2200,
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Tạo tài khoản" subtitle="Tài khoản người dùng không được tạo công khai trong hệ thống này.">
      {error && <Alert tone="error">{error}</Alert>}
      <div className="card" style={{ padding: 18, borderColor: 'var(--border-strong)', background: 'var(--surface-2)' }}>
        <p className="small muted" style={{ lineHeight: 1.6 }}>
          Chỉ quản trị viên mới có quyền cấp tài khoản cho sinh viên và giảng viên.
          Nếu bạn là người dùng hệ thống, hãy quay lại trang đăng nhập để sử dụng tài khoản đã được cấp.
        </p>
      </div>

      <div className="auth-foot" style={{ marginTop: 18 }}>
        Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
      </div>
    </AuthShell>
  );
};

export default Register;
