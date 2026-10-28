import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  AwardIcon, BookIcon, CalendarIcon, ClipboardIcon, LayersIcon, StudentsIcon,
  TrendingIcon, UserIcon, UsersIcon,
} from '../components/Icons';
import { EmptyState, LoadingBlock, PageHeader, StatCard, StatusBadge } from '../components/UI';
import { cohortService } from '../services/dashboardService';
import { formatScore, gradeLabel } from '../utils/format';

const StatLink = ({ to, children }) => (
  <Link to={to} style={{ display: 'block' }}>{children}</Link>
);

const Dashboard = () => {
  const { user } = useAuth();
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const displayName = user?.fullName || user?.username || 'bạn';
  const role = user?.role;

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    cohortService.load(role)
      .then((result) => { if (mounted) setData(result); })
      .catch(() => { if (mounted) toast.error('Không tải được dữ liệu', 'Vui lòng kiểm tra kết nối tới máy chủ.'); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [role]); // eslint-disable-line react-hooks/exhaustive-deps

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 11) return 'Chào buổi sáng';
    if (hour < 14) return 'Chào buổi trưa';
    if (hour < 18) return 'Chào buổi chiều';
    return 'Chào buổi tối';
  }, []);

  const roleText = { ADMIN: 'Quản trị viên', LECTURER: 'Giảng viên', STUDENT: 'Sinh viên' }[role] || 'Người dùng';

  if (loading) {
    return (
      <>
        <PageHeader title="Tổng quan" subtitle="Đang tổng hợp dữ liệu học vụ của bạn..." />
        <div className="card"><LoadingBlock /></div>
      </>
    );
  }

  return (
    <>
      <div className="card" style={{ marginBottom: 22, background: 'linear-gradient(135deg, #0d2551 0%, #123367 55%, #1d4ed8 130%)', border: 'none', overflow: 'hidden', position: 'relative' }}>
        <div style={{ padding: '28px 30px', color: '#fff', position: 'relative', zIndex: 1 }}>
          <span className="badge" style={{ background: 'rgba(96,165,250,.2)', color: '#bfdbfe' }}>{roleText}</span>
          <h1 style={{ fontSize: 26, fontWeight: 800, letterSpacing: '-.02em', margin: '14px 0 8px' }}>
            {greeting}, {displayName}
          </h1>
          <p style={{ color: '#b9cbea', fontSize: 13.5, maxWidth: 620 }}>
            {role === 'ADMIN' && 'Theo dõi toàn cảnh hoạt động đào tạo và quản lý dữ liệu học vụ của nhà trường.'}
            {role === 'LECTURER' && 'Xem lớp học phần được phân công và cập nhật điểm cho sinh viên.'}
            {role === 'STUDENT' && 'Theo dõi học phần đã đăng ký, thời khoá biểu và kết quả học tập của bạn.'}
          </p>
          <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
            {role === 'ADMIN' && <Link to="/admin/students" className="btn btn-primary">Quản lý sinh viên</Link>}
            {role === 'LECTURER' && <Link to="/lecturer/grades" className="btn btn-primary">Nhập điểm ngay</Link>}
            {role === 'STUDENT' && <Link to="/student/courses" className="btn btn-primary">Đăng ký học phần</Link>}
            <Link to={role === 'STUDENT' ? '/student/profile' : '/dashboard'} className="btn btn-secondary" style={{ background: 'rgba(255,255,255,.12)', borderColor: 'rgba(255,255,255,.22)', color: '#fff' }}>
              {role === 'STUDENT' ? 'Hồ sơ của tôi' : 'Xem tổng quan'}
            </Link>
          </div>
        </div>
        <div style={{ position: 'absolute', right: -60, top: -60, width: 260, height: 260, borderRadius: '50%', background: 'radial-gradient(circle, rgba(96,165,250,.35), transparent 65%)' }} />
      </div>

      {role === 'ADMIN' && data && <AdminDashboard data={data} />}
      {role === 'LECTURER' && data && <LecturerDashboard data={data} />}
      {role === 'STUDENT' && data && <StudentDashboard data={data} />}
    </>
  );
};

const AdminDashboard = ({ data }) => (
  <>
    <div className="stats-grid">
      <StatLink to="/admin/students"><StatCard label="Tổng sinh viên" value={data.totalStudents} icon={<StudentsIcon />} tone="blue" /></StatLink>
      <StatLink to="/admin/lecturers"><StatCard label="Tổng giảng viên" value={data.totalLecturers} icon={<UsersIcon />} tone="green" /></StatLink>
      <StatLink to="/admin/courses"><StatCard label="Môn học" value={data.totalCourses} icon={<BookIcon />} tone="amber" /></StatLink>
      <StatLink to="/admin/sections"><StatCard label="Lớp học phần" value={data.totalSections} icon={<LayersIcon />} tone="indigo" /></StatLink>
    </div>

    <div className="split">
      <div className="card">
        <div className="card-head">
          <div>
            <h2>Lớp học phần gần đây</h2>
            <p>Các lớp được cập nhật trong hệ thống</p>
          </div>
          <Link to="/admin/sections" className="btn btn-secondary btn-sm">Xem tất cả</Link>
        </div>
        {data.recentSections.length === 0 ? (
          <EmptyState title="Chưa có lớp học phần" description="Tạo học kỳ và lớp học phần để bắt đầu." />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr><th>Mã lớp</th><th>Môn học</th><th>Học kỳ</th><th>Sĩ số</th><th>Trạng thái</th></tr>
              </thead>
              <tbody>
                {data.recentSections.map((s) => (
                  <tr key={s.id}>
                    <td className="cell-strong">{s.sectionCode}</td>
                    <td>{s.course?.courseName || '—'}</td>
                    <td className="cell-muted">{s.semester?.semesterName || '—'}</td>
                    <td>{s.capacity}</td>
                    <td><StatusBadge status={s.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="stack">
        <div className="card">
          <div className="card-head"><h2>Trạng thái lớp</h2></div>
          <div className="card-body" style={{ display: 'grid', gap: 14 }}>
            {data.statusBreakdown.map((item) => (
              <div key={item.status}>
                <div className="flex-between" style={{ marginBottom: 6 }}>
                  <StatusBadge status={item.status} />
                  <span className="small" style={{ fontWeight: 700 }}>{item.count}</span>
                </div>
                <div style={{ height: 7, background: 'var(--surface-2)', borderRadius: 999, overflow: 'hidden' }}>
                  <div style={{ width: `${item.percent}%`, height: '100%', background: 'var(--blue-500)', borderRadius: 999 }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <div className="card-head"><h2>Thao tác nhanh</h2></div>
          <div className="card-body" style={{ display: 'grid', gap: 10 }}>
            <Link to="/admin/semesters" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><CalendarIcon size={17} /> Quản lý học kỳ</Link>
            <Link to="/admin/courses" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><BookIcon size={17} /> Quản lý môn học</Link>
            <Link to="/admin/lecturers" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><UsersIcon size={17} /> Quản lý giảng viên</Link>
          </div>
        </div>
      </div>
    </div>
  </>
);

const LecturerDashboard = ({ data }) => (
  <>
    <div className="stats-grid">
      <StatCard label="Lớp được phân công" value={data.sectionCount} icon={<LayersIcon />} tone="blue" />
      <StatCard label="Lớp đang mở" value={data.openCount} icon={<BookIcon />} tone="green" />
      <StatCard label="Tổng sĩ số" value={data.totalCapacity} icon={<StudentsIcon />} tone="amber" />
      <StatCard label="Sinh viên đã đăng ký" value={data.enrolledCount} icon={<UsersIcon />} tone="indigo" />
    </div>

    <div className="split">
      <div className="card">
        <div className="card-head">
          <div><h2>Lớp học phần của tôi</h2><p>Các lớp bạn phụ trách giảng dạy</p></div>
          <Link to="/lecturer/sections" className="btn btn-secondary btn-sm">Xem tất cả</Link>
        </div>
        {data.sections.length === 0 ? (
          <EmptyState title="Chưa được phân công lớp" description="Liên hệ quản trị viên để được phân công lớp học phần." />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Mã lớp</th><th>Môn học</th><th>Lịch học</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {data.sections.slice(0, 6).map((s) => (
                  <tr key={s.id}>
                    <td className="cell-strong">{s.sectionCode}</td>
                    <td>{s.course?.courseName || '—'}</td>
                    <td className="cell-muted">{s.schedule || '—'}</td>
                    <td><StatusBadge status={s.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head"><h2>Lối tắt</h2></div>
        <div className="card-body" style={{ display: 'grid', gap: 10 }}>
          <Link to="/lecturer/grades" className="btn btn-primary" style={{ justifyContent: 'flex-start' }}><ClipboardIcon size={17} /> Nhập điểm cho lớp</Link>
          <Link to="/lecturer/sections" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><LayersIcon size={17} /> Danh sách lớp học phần</Link>
          <Link to="/dashboard" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><TrendingIcon size={17} /> Xem tổng quan</Link>
        </div>
      </div>
    </div>
  </>
);

const StudentDashboard = ({ data }) => (
  <>
    <div className="stats-grid">
      <StatCard label="Học phần đã đăng ký" value={data.enrolledCount} icon={<BookIcon />} tone="blue" />
      <StatCard label="Tín chỉ tích luỹ" value={data.totalCredits} icon={<AwardIcon />} tone="green" />
      <StatCard label="Điểm trung bình" value={data.averageScore || '—'} icon={<TrendingIcon />} tone="amber" />
      <StatCard label="Lớp đang mở" value={data.openSections} icon={<LayersIcon />} tone="indigo" />
    </div>

    <div className="split">
      <div className="card">
        <div className="card-head">
          <div><h2>Học phần đã đăng ký</h2><p>Lịch học và trạng thái đăng ký của bạn</p></div>
          <Link to="/student/enrollments" className="btn btn-secondary btn-sm">Xem tất cả</Link>
        </div>
        {data.enrollments.length === 0 ? (
          <EmptyState
            title="Chưa đăng ký học phần nào"
            description="Khám phá các lớp học phần đang mở và đăng ký ngay."
            action={<Link to="/student/courses" className="btn btn-primary">Đăng ký học phần</Link>}
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Môn học</th><th>Mã lớp</th><th>Lịch học</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {data.enrollments.slice(0, 6).map((e) => (
                  <tr key={e.id}>
                    <td className="cell-strong">{e.courseSection?.course?.courseName || '—'}</td>
                    <td>{e.courseSection?.sectionCode || '—'}</td>
                    <td className="cell-muted">{e.courseSection?.schedule || '—'}</td>
                    <td><StatusBadge status={e.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="stack">
        <div className="card">
          <div className="card-head">
            <h2>Kết quả gần đây</h2>
            <Link to="/student/grades" className="small" style={{ color: 'var(--blue-600)', fontWeight: 600 }}>Chi tiết</Link>
          </div>
          <div className="card-body">
            {data.graded.length === 0 ? (
              <p className="muted small">Chưa có điểm được cập nhật.</p>
            ) : (
              <div className="stack" style={{ gap: 12 }}>
                {data.graded.slice(0, 4).map((e) => {
                  const score = e.grade?.finalScore;
                  const gl = gradeLabel(score);
                  return (
                    <div key={e.id} className="flex-between">
                      <div style={{ minWidth: 0 }}>
                        <div className="small" style={{ fontWeight: 600 }}>{e.courseSection?.course?.courseName || '—'}</div>
                        <div className="small muted">{e.courseSection?.sectionCode}</div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontWeight: 700 }}>{formatScore(score)}</div>
                        <span className={`badge badge-${gl.tone}`}>{gl.label}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-head"><h2>Lối tắt</h2></div>
          <div className="card-body" style={{ display: 'grid', gap: 10 }}>
            <Link to="/student/courses" className="btn btn-primary" style={{ justifyContent: 'flex-start' }}><BookIcon size={17} /> Đăng ký học phần</Link>
            <Link to="/student/grades" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><AwardIcon size={17} /> Kết quả học tập</Link>
            <Link to="/student/profile" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><UserIcon size={17} /> Hồ sơ cá nhân</Link>
          </div>
        </div>
      </div>
    </div>
  </>
);

export default Dashboard;
