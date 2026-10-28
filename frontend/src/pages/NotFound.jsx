import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeftIcon, HomeIcon, SearchIcon } from '../components/Icons';

const NotFound = () => {
  const navigate = useNavigate();

  return (
    <div className="error-page">
      <div className="error-card">
        <div className="empty-icon" style={{ margin: '0 auto 20px', width: 68, height: 68, borderRadius: 20, background: 'var(--blue-50)', color: 'var(--blue-600)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <SearchIcon size={32} />
        </div>
        <div className="error-code">404</div>
        <h1>Không tìm thấy trang</h1>
        <p>Đường dẫn bạn truy cập không tồn tại hoặc đã được di chuyển.</p>
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

export default NotFound;
