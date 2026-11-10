import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import { Alert, Field } from '../components/UI';
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  ClockIcon,
  EyeIcon,
  EyeOffIcon,
  LockIcon,
  MailIcon,
  ShieldIcon,
} from '../components/Icons';
import { authApi } from '../api';
import { useToast } from '../context/ToastContext';
import { getErrorMessage } from '../utils/format';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const paramEmail = searchParams.get('email') || '';
  const paramOtp = searchParams.get('otp') || searchParams.get('token') || '';

  const [email, setEmail] = useState(paramEmail);
  const [otp, setOtp] = useState(paramOtp);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const toast = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    if (paramEmail && !email) setEmail(paramEmail);
    if (paramOtp && !otp) setOtp(paramOtp);
  }, [paramEmail, paramOtp]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const cleanEmail = email.trim().toLowerCase();
    const cleanOtp = otp.trim();

    if (!cleanOtp) {
      setError('Vui lòng nhập mã OTP xác thực (6 chữ số).');
      return;
    }

    if (!newPassword) {
      setError('Vui lòng nhập mật khẩu mới.');
      return;
    }

    if (newPassword.length < 6) {
      setError('Mật khẩu mới phải có tối thiểu 6 ký tự.');
      return;
    }

    if (newPassword.startsWith(' ') || newPassword.endsWith(' ')) {
      setError('Mật khẩu không được chứa khoảng trắng ở đầu hoặc cuối.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không trùng khớp. Vui lòng kiểm tra lại.');
      return;
    }

    setLoading(true);
    try {
      const res = await authApi.resetPassword({
        email: cleanEmail,
        otp: cleanOtp,
        token: cleanOtp,
        newPassword,
      });

      setSuccess(true);
      toast.showAuthModal({
        icon: 'success',
        title: 'Đặt lại mật khẩu thành công',
        message: res.data?.message || 'Mật khẩu của bạn đã được cập nhật thành công. Đang chuyển về trang đăng nhập...',
        duration: 3000,
      });

      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 3000);
    } catch (err) {
      const msg = getErrorMessage(err, 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra lại mã OTP.');
      setError(msg);
      toast.showAuthModal({
        icon: 'error',
        title: 'Đặt lại mật khẩu thất bại',
        message: msg,
        duration: 3000,
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell
      title="Đặt lại mật khẩu"
      subtitle="Nhập mã OTP 6 chữ số và mật khẩu mới cho tài khoản của bạn."
    >
      {error && (
        <Alert tone="error">
          {error}
          {error.includes('hết hạn') || error.includes('sử dụng') ? (
            <div style={{ marginTop: 8 }}>
              <Link to="/forgot-password" style={{ fontWeight: 600, textDecoration: 'underline' }}>
                Bấm vào đây để yêu cầu mã OTP mới →
              </Link>
            </div>
          ) : null}
        </Alert>
      )}

      {success ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <Alert tone="success">
            <strong>
              <CheckCircleIcon size={16} /> Mật khẩu đã được cập nhật thành công!
            </strong>
            <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.5 }}>
              Bạn có thể sử dụng mật khẩu mới để đăng nhập ngay bây giờ. Hệ thống sẽ tự động chuyển hướng về trang đăng nhập sau giây lát...
            </div>
          </Alert>

          <Link to="/login" className="btn btn-primary btn-block">
            Đăng nhập ngay →
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 12,
              color: 'var(--muted)',
              marginBottom: 16,
              padding: '8px 12px',
              background: 'var(--navy-50, #f8fafc)',
              borderRadius: 'var(--radius-sm, 8px)',
              border: '1px solid var(--border, #e2e8f0)',
            }}
          >
            <ClockIcon size={14} style={{ color: 'var(--blue-600, #2563eb)', flexShrink: 0 }} />
            <span>Mã OTP bảo mật có hiệu lực trong <strong>5 phút</strong> (tối đa 5 lần nhập sai, bảo mật SHA-256).</span>
          </div>

          <Field label="Email tài khoản" hint="Không bắt buộc nếu đã có mã OTP hợp lệ">
            <div style={{ position: 'relative' }}>
              <MailIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
              <input
                className="input"
                style={{ paddingLeft: 38 }}
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="VD: student@sis.edu.vn hoặc lecturer@sis.edu.vn"
                disabled={loading}
              />
            </div>
          </Field>

          <Field label="Mã xác thực OTP (6 chữ số)" required hint="Mã OTP đã gửi về email của bạn">
            <div style={{ position: 'relative' }}>
              <ShieldIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
              <input
                className={`input${error && !otp ? ' invalid' : ''}`}
                style={{
                  paddingLeft: 38,
                  fontSize: 18,
                  fontWeight: 700,
                  letterSpacing: 6,
                  fontFamily: 'monospace',
                }}
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                autoFocus={!otp}
                disabled={loading}
              />
            </div>
          </Field>

          <Field label="Mật khẩu mới" required hint="Tối thiểu 6 ký tự, không có khoảng trắng ở đầu hoặc cuối">
            <div style={{ position: 'relative' }}>
              <LockIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
              <input
                className={`input${error && !newPassword ? ' invalid' : ''}`}
                style={{ paddingLeft: 38, paddingRight: 42 }}
                type={showNewPassword ? 'text' : 'password'}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Nhập mật khẩu mới"
                autoComplete="new-password"
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowNewPassword((v) => !v)}
                aria-label={showNewPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                style={{ position: 'absolute', right: 6, top: 5, border: 'none', background: 'none', color: 'var(--muted)', padding: 6, cursor: 'pointer' }}
              >
                {showNewPassword ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
              </button>
            </div>
          </Field>

          <Field label="Xác nhận mật khẩu mới" required>
            <div style={{ position: 'relative' }}>
              <LockIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
              <input
                className={`input${error && (!confirmPassword || confirmPassword !== newPassword) ? ' invalid' : ''}`}
                style={{ paddingLeft: 38, paddingRight: 42 }}
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Nhập lại mật khẩu mới"
                autoComplete="new-password"
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword((v) => !v)}
                aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                style={{ position: 'absolute', right: 6, top: 5, border: 'none', background: 'none', color: 'var(--muted)', padding: 6, cursor: 'pointer' }}
              >
                {showConfirmPassword ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
              </button>
            </div>
          </Field>

          <button className="btn btn-primary btn-block" type="submit" disabled={loading} style={{ marginTop: 10 }}>
            {loading ? (
              <>
                <span className="spinner" style={{ borderTopColor: '#fff', borderColor: 'rgba(255,255,255,.35)' }} />
                Đang lưu mật khẩu mới...
              </>
            ) : (
              'Xác nhận đặt lại mật khẩu'
            )}
          </button>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: 18,
              fontSize: 13,
            }}
          >
            <Link to="/forgot-password" style={{ color: 'var(--blue-600, #2563eb)' }}>
              Yêu cầu gửi lại mã OTP
            </Link>
            <Link to="/login" style={{ color: 'var(--muted)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <ArrowLeftIcon size={14} /> Quay lại đăng nhập
            </Link>
          </div>
        </form>
      )}
    </AuthShell>
  );
};

export default ResetPassword;
