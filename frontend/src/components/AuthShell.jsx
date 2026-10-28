import React from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeftIcon, BookIcon, CheckCircleIcon, ClipboardIcon, ShieldIcon, StudentsIcon,
} from './Icons';

const points = [
  { icon: <StudentsIcon />, title: 'Quản lý hồ sơ tập trung', desc: 'Thông tin sinh viên, giảng viên luôn đồng bộ.' },
  { icon: <BookIcon />, title: 'Đăng ký học phần nhanh', desc: 'Kiểm soát sĩ số, trạng thái lớp theo thời gian thực.' },
  { icon: <ClipboardIcon />, title: 'Điểm số minh bạch', desc: 'Nhập điểm theo lớp, khoá sổ và tra cứu dễ dàng.' },
];

const AuthShell = ({ title, subtitle, children }) => (
  <div className="auth-shell">
    <aside className="auth-aside">
      <Link to="/" className="auth-brand" style={{ color: '#fff' }}>
        <div className="brand-logo">SIS</div>
        <div className="brand-text">
          <strong>Student Information</strong>
          <span>Hệ thống quản lý đào tạo</span>
        </div>
      </Link>

      <div className="auth-hero">
        <span className="eyebrow" style={{ background: 'rgba(96,165,250,.14)', color: '#bfdbfe', borderColor: 'rgba(96,165,250,.25)' }}>
          <ShieldIcon size={14} /> Nền tảng học vụ an toàn
        </span>
        <h1 style={{ marginTop: 18 }}>Quản lý đào tạo<br />liền mạch và tin cậy</h1>
        <p>
          Một không gian duy nhất cho quản trị viên, giảng viên và sinh viên —
          giúp công tác học vụ nhanh hơn, rõ ràng hơn và ít sai sót hơn.
        </p>
        <div className="auth-points">
          {points.map((p) => (
            <div className="auth-point" key={p.title}>
              <div className="pt-icon">{p.icon}</div>
              <div>
                <strong>{p.title}</strong>
                <span>{p.desc}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="auth-aside-foot">© {new Date().getFullYear()} Student Information System</div>
    </aside>

    <main className="auth-main">
      <div className="auth-card">
        <div className="auth-mobile-brand">
          <div className="brand-logo">SIS</div>
          <div className="brand-text">
            <strong style={{ color: 'var(--navy-900)' }}>Student Information</strong>
            <span style={{ color: 'var(--muted)' }}>Hệ thống quản lý đào tạo</span>
          </div>
        </div>
        <div className="card">
          <div className="auth-title">
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {children}
        </div>
        <div className="auth-foot">
          <Link to="/"><ArrowLeftIcon size={14} style={{ verticalAlign: 'middle', marginRight: 4 }} /> Về trang chủ</Link>
        </div>
      </div>
    </main>
  </div>
);

export default AuthShell;
