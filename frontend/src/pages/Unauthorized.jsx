import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeftIcon, HomeIcon, ShieldIcon } from '../components/Icons';
import { useAuth } from '../context/AuthContext';

const Unauthorized = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  return (
    <div className="error-page">
      <div className="error-card">
        <div className="empty-icon" style={{ margin: '0 auto 20px', width: 68, height: 68, borderRadius: 20, background: 'var(--danger-bg)', color: 'var(--danger)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ShieldIcon size={32} />
        </div>
        <div className="error-code">403</div>
        <h1>Bạn không có quyền truy cập</h1>
        <p>
          Tài khoản <strong>{user?.username}</strong> không được phép truy cập trang này.
          Vui lòng quay lại trang phù hợp với vai trò của bạn.
        </p>
        <button className="btn btn-primary" onClick={() => navigate('/dashboard')}>
          <HomeIcon /> Về tổng quan
        </button>
        <button className="btn btn-secondary" onClick={() => navigate(-1)}>
          <ArrowLeftIcon /> Quay lại
        </button>
      </div>
    </div>
  );
};

export default Unauthorized;
