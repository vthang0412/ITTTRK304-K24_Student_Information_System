import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
  RefreshIcon,
  ShieldIcon,
} from '../components/Icons';
import { authApi } from '../api';
import { useToast } from '../context/ToastContext';
import { getErrorMessage } from '../utils/format';

const ForgotPassword = () => {
  const [step, setStep] = useState(1); // 1: nhập email, 2: nhập OTP và mật khẩu mới
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  // backendOtp không được hiển thị/lưu ra ngoài – chỉ dùng nội bộ để tự động điền ô OTP
  // khi Backend trả về trong response (môi trường dev).
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [success, setSuccess] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const toast = useToast();
  const navigate = useNavigate();

  // Đếm ngược gửi lại mã OTP
  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown]);

  // Bước 1: Gửi yêu cầu nhận mã OTP qua email
  const handleRequestOtp = async (e) => {
    if (e) e.preventDefault();
    setError('');
    const rawEmail = email.trim().toLowerCase();

    if (!rawEmail) {
      setError('Vui lòng nhập địa chỉ email của bạn.');
      return;
    }

    if (/\s/.test(rawEmail)) {
      setError('Email không được chứa khoảng trắng.');
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(rawEmail)) {
      setError('Định dạng email chưa đúng (VD: student@sis.edu.vn hoặc lecturer@sis.edu.vn).');
      return;
    }

    setLoading(true);
    try {
      const res = await authApi.forgotPassword({ email: rawEmail });
      const data = res.data || {};
      setStep(2);
      setCountdown(60);
      toast.success(
        'Đã gửi mã xác thực OTP',
        data.message || 'Mã OTP 6 số đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư (thời hạn 5 phút).'
      );
    } catch (err) {
      const errMsg = getErrorMessage(err, 'Không thể gửi yêu cầu đặt lại mật khẩu. Vui lòng kiểm tra lại.');
      setError(errMsg);
    } finally {
      setLoading(false);
    }
  };

  // Gửi lại mã OTP
  const handleResendOtp = async () => {
    if (countdown > 0 || resending) return;
    setError('');
    setResending(true);
    try {
      const res = await authApi.forgotPassword({ email: email.trim().toLowerCase() });
      const data = res.data || {};
      setCountdown(60);
      toast.success(
        'Đã gửi lại mã OTP',
        'Mã OTP mới đã được gửi tới email của bạn (hiệu lực 5 phút).'
      );
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể gửi lại mã OTP. Vui lòng thử lại sau.'));
    } finally {
      setResending(false);
    }
  };

  // Bước 2: Xác thực OTP và đặt mật khẩu mới
  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError('');

    const cleanOtp = otp.trim();
    if (!cleanOtp) {
      setError('Vui lòng nhập mã OTP 6 chữ số.');
      return;
    }

    if (!/^\d{6}$/.test(cleanOtp)) {
      setError('Mã OTP phải bao gồm đúng 6 chữ số.');
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
        email: email.trim().toLowerCase(),
        otp: cleanOtp,
        token: cleanOtp,
        newPassword,
      });

      setSuccess(true);
      toast.showAuthModal({
        icon: 'success',
        title: 'Đặt lại mật khẩu thành công',
        message:
          res.data?.message ||
          'Mật khẩu của bạn đã được cập nhật thành công. Đang chuyển về trang đăng nhập...',
        duration: 3000,
      });

      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 3000);
    } catch (err) {
      const rawErrMsg = err?.response?.data?.message || '';
      let msg = rawErrMsg || 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra lại mã OTP.';

      // Phân loại lỗi để hướng dẫn người dùng cụ thể
      const lc = msg.toLowerCase();
      let shouldGoBackToStep1 = false;
      if (lc.includes('hết hạn') || lc.includes('5 phút')) {
        // OTP đã hết hạn - cần quay lại bước 1
        shouldGoBackToStep1 = true;
      } else if (lc.includes('quá 5 lần') || lc.includes('vô hiệu hóa') || lc.includes('thao tác lại từ đầu')) {
        // OTP bị vô hiệu hoá do nhập sai quá nhiều - cần quay lại bước 1
        shouldGoBackToStep1 = true;
      }

      setError(msg);
      toast.showAuthModal({
        icon: 'error',
        title: 'Đặt lại mật khẩu thất bại',
        message: msg,
        duration: shouldGoBackToStep1 ? 0 : 3000,
        showConfirmButton: shouldGoBackToStep1,
        confirmButtonText: shouldGoBackToStep1 ? 'Yêu cầu mã OTP mới' : undefined,
      }).then((result) => {
        if (shouldGoBackToStep1 && result.isConfirmed) {
          handleBackToStep1();
        }
      });
    } finally {
      setLoading(false);
    }
  };

  const handleBackToStep1 = () => {
    setStep(1);
    setOtp('');
    setError('');
  };

  return (
    <AuthShell
      title={step === 1 ? 'Quên mật khẩu' : 'Xác thực OTP & Đặt mật khẩu'}
      subtitle={
        step === 1
          ? 'Nhập email tài khoản sinh viên hoặc giảng viên để nhận mã OTP xác thực qua email.'
          : 'Nhập mã OTP 6 chữ số nhận từ email và thiết lập mật khẩu mới.'
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      {success ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <Alert tone="success">
            <strong>
              <CheckCircleIcon size={16} /> Đặt lại mật khẩu thành công!
            </strong>
            <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.5 }}>
              Mật khẩu mới của bạn đã được lưu an toàn. Hệ thống sẽ tự động chuyển hướng về trang đăng nhập trong giây lát...
            </div>
          </Alert>
          <Link to="/login" className="btn btn-primary btn-block">
            Đến trang đăng nhập ngay →
          </Link>
        </div>
      ) : step === 1 ? (
        /* ======================== BƯỚC 1: NHẬP EMAIL ======================== */
        <form onSubmit={handleRequestOtp} noValidate>
          <Field label="Email tài khoản" required hint="Email đã đăng ký tại trường (sinh viên hoặc giảng viên)">
            <div style={{ position: 'relative' }}>
              <MailIcon size={17} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--muted)' }} />
              <input
                className={`input${error ? ' invalid' : ''}`}
                style={{ paddingLeft: 38 }}
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="VD: student@sis.edu.vn hoặc lecturer@sis.edu.vn"
                autoFocus
                disabled={loading}
              />
            </div>
          </Field>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 12,
              color: 'var(--muted)',
              marginBottom: 18,
              padding: '8px 12px',
              background: 'var(--navy-50, #f8fafc)',
              borderRadius: 'var(--radius-sm, 8px)',
              border: '1px solid var(--border, #e2e8f0)',
            }}
          >
            <ClockIcon size={15} style={{ color: 'var(--blue-600, #2563eb)', flexShrink: 0 }} />
            <span>Mã xác thực OTP có hiệu lực trong <strong>5 phút</strong> (tối đa 5 lần thử sai, bảo mật SHA-256).</span>
          </div>

          <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
            {loading ? (
              <>
                <span className="spinner" style={{ borderTopColor: '#fff', borderColor: 'rgba(255,255,255,.35)' }} />
                Đang gửi mã OTP...
              </>
            ) : (
              'Gửi mã xác thực OTP →'
            )}
          </button>

          <div className="auth-foot" style={{ marginTop: 20 }}>
            <Link to="/login" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <ArrowLeftIcon size={14} /> Quay lại đăng nhập
            </Link>
          </div>
        </form>
      ) : (
        /* ======================== BƯỚC 2: NHẬP OTP & MẬT KHẨU MỚI ======================== */
        <form onSubmit={handleResetPassword} noValidate>
          <div
            style={{
              padding: '10px 14px',
              background: 'var(--blue-50, #eff6ff)',
              border: '1px solid var(--blue-100, #dbeafe)',
              borderRadius: 'var(--radius-sm, 8px)',
              marginBottom: 16,
              fontSize: 13,
              color: 'var(--navy-900, #0d2551)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Email nhận OTP: <strong>{email}</strong></span>
              <button
                type="button"
                onClick={handleBackToStep1}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--blue-600, #2563eb)',
                  cursor: 'pointer',
                  fontSize: 12,
                  fontWeight: 600,
                  textDecoration: 'underline',
                  padding: 0,
                }}
              >
                Đổi email
              </button>
            </div>
          </div>

          <Field label="Mã xác thực OTP (6 chữ số)" required hint="Kiểm tra email của bạn để lấy mã OTP">
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
                autoFocus
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
              'Xác nhận đổi mật khẩu'
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
            <button
              type="button"
              onClick={handleResendOtp}
              disabled={countdown > 0 || resending}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                background: 'none',
                border: 'none',
                color: countdown > 0 ? 'var(--muted)' : 'var(--blue-600, #2563eb)',
                cursor: countdown > 0 ? 'default' : 'pointer',
                fontWeight: 500,
                padding: 0,
              }}
            >
              <RefreshIcon size={14} className={resending ? 'spin' : ''} />
              {countdown > 0 ? `Gửi lại mã OTP sau ${countdown}s` : 'Gửi lại mã OTP'}
            </button>

            <Link to="/login" style={{ color: 'var(--muted)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <ArrowLeftIcon size={14} /> Quay lại đăng nhập
            </Link>
          </div>
        </form>
      )}
    </AuthShell>
  );
};

export default ForgotPassword;
