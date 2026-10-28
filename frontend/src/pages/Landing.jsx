import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  ArrowRightIcon, AwardIcon, BookIcon, CalendarIcon, CheckIcon, ClipboardIcon,
  LayersIcon, ShieldIcon, StudentsIcon, TrendingIcon, UserIcon, UsersIcon,
} from '../components/Icons';

const features = [
  {
    icon: <StudentsIcon />, tone: 'tone-blue',
    title: 'Quản lý sinh viên',
    desc: 'Hồ sơ sinh viên, khoa/ngành và trạng thái học tập được quản lý tập trung, nhất quán.',
  },
  {
    icon: <BookIcon />, tone: 'tone-green',
    title: 'Đăng ký học phần',
    desc: 'Sinh viên chủ động đăng ký lớp học phần theo học kỳ, kiểm soát sĩ số và trạng thái lớp.',
  },
  {
    icon: <ClipboardIcon />, tone: 'tone-amber',
    title: 'Quản lý điểm',
    desc: 'Giảng viên nhập điểm theo lớp, hệ thống kiểm tra thang điểm và khoá sổ điểm minh bạch.',
  },
  {
    icon: <CalendarIcon />, tone: 'tone-indigo',
    title: 'Học kỳ & thời khoá biểu',
    desc: 'Thiết lập học kỳ, thời gian đăng ký và lịch giảng dạy cho từng lớp học phần.',
  },
  {
    icon: <ShieldIcon />, tone: 'tone-red',
    title: 'Phân quyền an toàn',
    desc: 'Xác thực JWT và phân quyền theo vai trò Quản trị viên, Giảng viên và Sinh viên.',
  },
  {
    icon: <TrendingIcon />, tone: 'tone-teal',
    title: 'Báo cáo tổng quan',
    desc: 'Bảng điều khiển trực quan với các chỉ số đào tạo cập nhật theo thời gian thực.',
  },
];

const Landing = () => {
  const { isAuthenticated } = useAuth();

  return (
    <div className="landing">
      <header className="landing-nav">
        <div className="landing-nav-inner">
          <Link to="/" className="auth-brand" style={{ color: 'var(--text)' }}>
            <div className="brand-logo">SIS</div>
            <div className="brand-text">
              <strong style={{ color: 'var(--navy-900)' }}>Student Information</strong>
              <span style={{ color: 'var(--muted)' }}>Hệ thống quản lý đào tạo</span>
            </div>
          </Link>
          <nav className="landing-menu">
            <a href="#features">Tính năng</a>
            <a href="#roles">Vai trò</a>
            <a href="#about">Giới thiệu</a>
          </nav>
          <div className="landing-actions">
            {isAuthenticated ? (
              <Link to="/dashboard" className="btn btn-primary">Vào hệ thống <ArrowRightIcon /></Link>
            ) : (
              <>
                <Link to="/login" className="btn btn-secondary">Đăng nhập</Link>
                <Link to="/register" className="btn btn-primary">Bắt đầu</Link>
              </>
            )}
          </div>
        </div>
      </header>

      <section className="landing-hero">
        <div className="landing-hero-inner">
          <div>
            <span className="eyebrow"><TrendingIcon size={14} /> Nền tảng quản lý đào tạo hiện đại</span>
            <h1>Quản lý học vụ <em>gọn gàng</em> và minh bạch hơn</h1>
            <p className="lead">
              Hệ thống thông tin sinh viên giúp nhà trường quản lý hồ sơ, học phần,
              điểm số và học kỳ trên một nền tảng duy nhất — nhanh chóng, chính xác
              và an toàn cho mọi vai trò.
            </p>
            <div className="landing-cta">
              <Link to={isAuthenticated ? '/dashboard' : '/login'} className="btn btn-primary">
                {isAuthenticated ? 'Mở bảng điều khiển' : 'Đăng nhập hệ thống'} <ArrowRightIcon />
              </Link>
              <a href="#features" className="btn btn-secondary">Khám phá tính năng</a>
            </div>
            <div className="landing-meta">
              <div><strong>03</strong><span>Vai trò người dùng</span></div>
              <div><strong>06</strong><span>Nghiệp vụ cốt lõi</span></div>
              <div><strong>100%</strong><span>Dữ liệu tập trung</span></div>
            </div>
          </div>

          <div className="hero-panel">
            <div className="hero-panel-head">
              <span className="hero-dot" style={{ background: '#fca5a5' }} />
              <span className="hero-dot" style={{ background: '#fcd34d' }} />
              <span className="hero-dot" style={{ background: '#86efac' }} />
              <span className="small muted" style={{ marginLeft: 8 }}>Bảng điều khiển học vụ</span>
            </div>
            <div className="hero-panel-body">
              <div className="hero-mini-stats">
                <div className="hero-mini-card">
                  <small>Sinh viên</small>
                  <strong style={{ color: 'var(--blue-600)' }}>1.248</strong>
                  <span className="badge badge-green" style={{ marginTop: 6 }}>Đang học</span>
                </div>
                <div className="hero-mini-card">
                  <small>Lớp học phần</small>
                  <strong style={{ color: 'var(--success)' }}>86</strong>
                  <span className="badge badge-blue" style={{ marginTop: 6 }}>Học kỳ này</span>
                </div>
              </div>
              <div className="hero-list-item">
                <span className="dot"><BookIcon /></span>
                <div style={{ flex: 1 }}>
                  <b>INT2204 — Lập trình hướng đối tượng</b>
                  <span>Thứ 2, 07:30 · Phòng A2-301</span>
                </div>
                <span className="badge badge-green">Đang mở</span>
              </div>
              <div className="hero-list-item">
                <span className="dot"><ClipboardIcon /></span>
                <div style={{ flex: 1 }}>
                  <b>INT3306 — Cơ sở dữ liệu</b>
                  <span>Thứ 4, 09:00 · Phòng B1-205</span>
                </div>
                <span className="badge badge-blue">Đã đăng ký</span>
              </div>
              <div className="hero-list-item">
                <span className="dot"><AwardIcon /></span>
                <div style={{ flex: 1 }}>
                  <b>Kết quả học tập</b>
                  <span>Cập nhật điểm và tiến độ tích luỹ</span>
                </div>
                <span className="badge badge-amber">Mới</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="landing-section" id="features">
        <div className="section-head">
          <h2>Mọi nghiệp vụ học vụ trong một nơi</h2>
          <p>Từ hồ sơ sinh viên đến điểm số, tất cả được tổ chức rõ ràng và dễ truy cập.</p>
        </div>
        <div className="feature-grid">
          {features.map((f) => (
            <article className="feature-card" key={f.title}>
              <div className={`feature-icon ${f.tone}`}>{f.icon}</div>
              <h3>{f.title}</h3>
              <p>{f.desc}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="landing-section" id="roles" style={{ paddingTop: 0 }}>
        <div className="section-head">
          <h2>Thiết kế cho từng vai trò</h2>
          <p>Mỗi người dùng có không gian làm việc phù hợp với trách nhiệm của mình.</p>
        </div>
        <div className="feature-grid">
          <article className="feature-card">
            <div className="feature-icon tone-blue"><UsersIcon /></div>
            <h3>Quản trị viên</h3>
            <p>Quản lý sinh viên, giảng viên, môn học, học kỳ và lớp học phần trên toàn hệ thống.</p>
            <ul style={{ marginTop: 14, display: 'grid', gap: 8 }}>
              {['Toàn quyền dữ liệu học vụ', 'Theo dõi chỉ số đào tạo', 'Cấu hình lớp & học kỳ'].map((i) => (
                <li key={i} className="small muted" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <CheckIcon size={15} style={{ color: 'var(--success)' }} /> {i}
                </li>
              ))}
            </ul>
          </article>
          <article className="feature-card">
            <div className="feature-icon tone-green"><UserIcon /></div>
            <h3>Giảng viên</h3>
            <p>Theo dõi lớp học phần được phân công, quản lý danh sách sinh viên và nhập điểm.</p>
            <ul style={{ marginTop: 14, display: 'grid', gap: 8 }}>
              {['Xem lớp được phân công', 'Nhập và cập nhật điểm', 'Kiểm soát trạng thái sổ điểm'].map((i) => (
                <li key={i} className="small muted" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <CheckIcon size={15} style={{ color: 'var(--success)' }} /> {i}
                </li>
              ))}
            </ul>
          </article>
          <article className="feature-card">
            <div className="feature-icon tone-amber"><LayersIcon /></div>
            <h3>Sinh viên</h3>
            <p>Tra cứu hồ sơ, đăng ký học phần theo học kỳ và theo dõi kết quả học tập cá nhân.</p>
            <ul style={{ marginTop: 14, display: 'grid', gap: 8 }}>
              {['Đăng ký / huỷ học phần', 'Xem thời khoá biểu', 'Tra cứu điểm số'].map((i) => (
                <li key={i} className="small muted" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <CheckIcon size={15} style={{ color: 'var(--success)' }} /> {i}
                </li>
              ))}
            </ul>
          </article>
        </div>
      </section>

      <section className="landing-section" id="about" style={{ paddingTop: 0 }}>
        <div className="landing-cta-band">
          <div>
            <h2>Sẵn sàng số hoá công tác học vụ?</h2>
            <p>Đăng nhập để bắt đầu quản lý hồ sơ, học phần và kết quả học tập ngay hôm nay.</p>
          </div>
          <Link to={isAuthenticated ? '/dashboard' : '/login'} className="btn btn-primary" style={{ height: 46, padding: '0 22px' }}>
            {isAuthenticated ? 'Vào hệ thống' : 'Đăng nhập ngay'} <ArrowRightIcon />
          </Link>
        </div>
      </section>

      <footer className="landing-footer">
        <div className="landing-footer-inner">
          <span>© {new Date().getFullYear()} Student Information System. Hệ thống quản lý đào tạo.</span>
          <span>Được xây dựng với React & Spring Boot</span>
        </div>
      </footer>
    </div>
  );
};

export default Landing;
