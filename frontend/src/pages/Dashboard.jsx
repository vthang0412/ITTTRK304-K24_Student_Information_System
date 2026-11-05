import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  AwardIcon, BookIcon, CalendarIcon, ClipboardIcon, LayersIcon, StudentsIcon,
  TrendingIcon, UserIcon, UsersIcon,
} from '../components/Icons';
import { EmptyState, LoadingBlock, PageHeader, SearchInput, StatCard, StatusBadge } from '../components/UI';
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

const AdminDashboard = ({ data }) => {
  const academicAlerts = data?.academicAlerts || {};
  const lowGpaStudents = academicAlerts.lowGpaStudents || [];
  const lowCreditStudents = academicAlerts.lowCreditStudents || [];
  const notRegisteredStudents = academicAlerts.notRegisteredStudents || [];
  const passRateByDepartment = academicAlerts.passRateByDepartment || [];
  const currentSemester = academicAlerts.currentSemester || null;

  const [gpaSearch, setGpaSearch] = useState('');
  const [lowCreditSearch, setLowCreditSearch] = useState('');
  const [enrollmentSearch, setEnrollmentSearch] = useState('');
  const [gpaDepartment, setGpaDepartment] = useState('ALL');

  const allDepartments = useMemo(() => {
    const values = new Set();
    [...lowGpaStudents, ...lowCreditStudents, ...notRegisteredStudents].forEach((student) => {
      if (student.department) values.add(student.department);
    });
    return [...values].sort((a, b) => a.localeCompare(b));
  }, [lowGpaStudents, lowCreditStudents, notRegisteredStudents]);

  const filteredLowGpa = useMemo(() => {
    const keyword = gpaSearch.trim().toLowerCase();
    return [...lowGpaStudents]
      .filter((student) => {
        if (gpaDepartment !== 'ALL' && student.department !== gpaDepartment) return false;
        if (!keyword) return true;
        return [student.studentCode, student.fullName, student.department].join(' ').toLowerCase().includes(keyword);
      })
      .sort((left, right) => left.gpa - right.gpa);
  }, [lowGpaStudents, gpaSearch, gpaDepartment]);

  const filteredLowCredit = useMemo(() => {
    const keyword = lowCreditSearch.trim().toLowerCase();
    return [...lowCreditStudents]
      .filter((student) => {
        if (!keyword) return true;
        return [student.studentCode, student.fullName, student.department].join(' ').toLowerCase().includes(keyword);
      })
      .sort((left, right) => left.completedCredits - right.completedCredits);
  }, [lowCreditStudents, lowCreditSearch]);

  const filteredNotRegistered = useMemo(() => {
    const keyword = enrollmentSearch.trim().toLowerCase();
    return [...notRegisteredStudents]
      .filter((student) => {
        if (!keyword) return true;
        return [student.studentCode, student.fullName, student.department].join(' ').toLowerCase().includes(keyword);
      })
      .sort((left, right) => (left.fullName || '').localeCompare(right.fullName || ''));
  }, [notRegisteredStudents, enrollmentSearch]);

  const totalStudents = Number(data.totalStudents || 0);
  const lowGpaCount = lowGpaStudents.length;
  const lowCreditCount = lowCreditStudents.length;
  const notRegisteredCount = notRegisteredStudents.length;
  const passCount = passRateByDepartment.reduce((sum, item) => sum + (item.passRate || 0), 0);
  const totalDepartments = passRateByDepartment.length || 1;
  const averagePassRate = totalDepartments ? Math.round(passCount / totalDepartments) : 0;
  const averageFailRate = 100 - averagePassRate;

  return (
    <>
      <div className="stats-grid">
        <StatLink to="/admin/students"><StatCard label="Tổng sinh viên" value={data.totalStudents} icon={<StudentsIcon />} tone="blue" /></StatLink>
        <StatCard label="Sinh viên GPA thấp" value={lowGpaCount} icon={<TrendingIcon />} tone="red" />
        <StatCard label="Sinh viên tín chỉ thấp" value={lowCreditCount} icon={<AwardIcon />} tone="amber" />
        <StatCard label="Chưa đăng ký học phần" value={notRegisteredCount} icon={<CalendarIcon />} tone="indigo" />
        <StatCard label="Tỷ lệ đạt" value={totalStudents ? `${averagePassRate}%` : '—'} icon={<AwardIcon />} tone="green" />
        <StatCard label="Tỷ lệ trượt" value={totalStudents ? `${averageFailRate}%` : '—'} icon={<TrendingIcon />} tone="red" />
      </div>

      <div className="card alert-panel" style={{ marginBottom: 22 }}>
        <div className="card-head">
          <div>
            <h2>Cảnh báo học tập</h2>
            <p>Thống kê được tính từ dữ liệu điểm, đăng ký và học kỳ hiện có trên backend</p>
          </div>
        </div>
        <div className="card-body">
          <div className="alert-note">
            <strong>Ngưỡng cảnh báo:</strong>
            <span>GPA dưới {academicAlerts.gpaThreshold ?? 2.5} và tích lũy dưới {academicAlerts.lowCreditThreshold ?? 12} tín chỉ được đánh dấu cảnh báo. Dữ liệu được tính từ điểm và đăng ký thực tế từ hệ thống.</span>
          </div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 22 }}>
        <div className="card-head">
          <div>
            <h2>Thống kê tổng quan</h2>
            <p>Tỷ lệ tài khoản và trạng thái hoạt động trong hệ thống</p>
          </div>
        </div>
        <div className="card-body">
          <div className="chart-layout">
            <div className="chart-panel" style={{ height: 260 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={data.accountOverview} dataKey="value" nameKey="name" innerRadius={54} outerRadius={88} paddingAngle={3}>
                    {data.accountOverview.map((entry) => (
                      <Cell key={entry.name} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => [`${value}`, 'Số lượng']} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="chart-legend">
              {data.accountOverview.map((item) => (
                <div key={item.name} className="chart-legend-item">
                  <span className="chart-dot" style={{ background: item.color }} />
                  <div style={{ flex: 1 }}>
                    <span>{item.name}</span>
                  </div>
                  <strong>{item.value}</strong>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="split" style={{ marginBottom: 22 }}>
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Sinh viên có GPA thấp</h2>
              <p>Danh sách sinh viên đang nằm dưới ngưỡng cảnh báo</p>
            </div>
          </div>
          <div className="card-body">
            <div className="toolbar" style={{ marginBottom: 14 }}>
              <SearchInput value={gpaSearch} onChange={setGpaSearch} placeholder="Tìm mã / tên / lớp" />
              <select className="select" value={gpaDepartment} onChange={(event) => setGpaDepartment(event.target.value)} style={{ maxWidth: 180 }}>
                <option value="ALL">Tất cả lớp</option>
                {allDepartments.map((department) => (
                  <option key={department} value={department}>{department}</option>
                ))}
              </select>
            </div>
            {filteredLowGpa.length === 0 ? (
              <EmptyState title="Không có sinh viên GPA thấp" description="Hiện không có dữ liệu nào dưới ngưỡng cảnh báo GPA." />
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr><th>Mã SV</th><th>Họ tên</th><th>Lớp</th><th>GPA</th><th>Học kỳ</th><th>Trạng thái</th></tr>
                  </thead>
                  <tbody>
                    {filteredLowGpa.map((student) => (
                      <tr key={student.id}>
                        <td className="cell-strong">{student.studentCode || '—'}</td>
                        <td>{student.fullName || '—'}</td>
                        <td>{student.department || 'Chưa xác định'}</td>
                        <td>{Number(student.gpa).toFixed(2)}</td>
                        <td>{currentSemester?.semesterName || '—'}</td>
                        <td><span className="badge badge-red">Cảnh báo</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <div>
              <h2>Sinh viên tích lũy tín chỉ thấp</h2>
              <p>Dựa trên số tín chỉ học phần đã hoàn thành</p>
            </div>
          </div>
          <div className="card-body">
            <div className="toolbar" style={{ marginBottom: 14 }}>
              <SearchInput value={lowCreditSearch} onChange={setLowCreditSearch} placeholder="Tìm mã / tên / lớp" />
            </div>
            {filteredLowCredit.length === 0 ? (
              <EmptyState title="Không có cảnh báo tín chỉ" description="Tất cả sinh viên đã tích lũy đủ tín chỉ theo ngưỡng báo động hiện tại." />
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr><th>Mã SV</th><th>Họ tên</th><th>Lớp</th><th>Tín chỉ đã tích lũy</th><th>Mức độ</th></tr>
                  </thead>
                  <tbody>
                    {filteredLowCredit.map((student) => (
                      <tr key={student.id}>
                        <td className="cell-strong">{student.studentCode || '—'}</td>
                        <td>{student.fullName || '—'}</td>
                        <td>{student.department || 'Chưa xác định'}</td>
                        <td>{student.completedCredits}</td>
                        <td><span className="badge badge-amber">Cần chú ý</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="split" style={{ marginBottom: 22 }}>
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Sinh viên chưa đăng ký học phần</h2>
              <p>{currentSemester?.semesterName ? `Học kỳ đang xét: ${currentSemester.semesterName}` : 'Học kỳ hiện tại chưa xác định'}</p>
            </div>
          </div>
          <div className="card-body">
            <div className="toolbar" style={{ marginBottom: 14 }}>
              <SearchInput value={enrollmentSearch} onChange={setEnrollmentSearch} placeholder="Tìm mã / tên / lớp" />
            </div>
            {filteredNotRegistered.length === 0 ? (
              <EmptyState title="Tất cả sinh viên đã đăng ký" description="Không có sinh viên nào bị thiếu đăng ký trong học kỳ hiện tại." />
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr><th>Mã SV</th><th>Họ tên</th><th>Lớp</th><th>Học kỳ</th><th>Trạng thái</th></tr>
                  </thead>
                  <tbody>
                    {filteredNotRegistered.map((student) => (
                      <tr key={student.id}>
                        <td className="cell-strong">{student.studentCode || '—'}</td>
                        <td>{student.fullName || '—'}</td>
                        <td>{student.department || 'Chưa xác định'}</td>
                        <td>{student.activeSemester || '—'}</td>
                        <td><span className="badge badge-amber">Chưa đăng ký</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <div>
              <h2>Tỷ lệ đạt / trượt theo lớp</h2>
              <p>So sánh kết quả học tập qua từng lớp trong dữ liệu hiện có</p>
            </div>
          </div>
          <div className="card-body">
            {passRateByDepartment.length === 0 ? (
              <EmptyState title="Chưa có dữ liệu theo lớp" description="Hệ thống chưa có đủ điểm để thống kê tỷ lệ đạt/trượt theo lớp." />
            ) : (
              <div style={{ height: 300 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={passRateByDepartment} margin={{ top: 20, right: 10, left: -10, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                    <XAxis dataKey="name" tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                    <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                    <Tooltip
                      formatter={(value) => [`${value}%`, 'Tỷ lệ']}
                      contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', boxShadow: 'var(--shadow-sm)' }}
                    />
                    <Legend />
                    <Bar dataKey="passRate" name="Đạt" fill="#16a34a" radius={[6, 6, 0, 0]} />
                    <Bar dataKey="failRate" name="Trượt" fill="#f59e0b" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
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
};

const LecturerDashboard = ({ data }) => (
  <>
    <div className="stats-grid">
      <StatCard label="Lớp được phân công" value={data.sectionCount} icon={<LayersIcon />} tone="blue" />
      <StatCard label="Lớp đang mở" value={data.openCount} icon={<BookIcon />} tone="green" />
      <StatCard label="Tổng sĩ số" value={data.totalCapacity} icon={<StudentsIcon />} tone="amber" />
      <StatCard label="Sinh viên đã đăng ký" value={data.enrolledCount} icon={<UsersIcon />} tone="indigo" />
    </div>

    <div className="card" style={{ marginBottom: 22 }}>
      <div className="card-head">
        <div>
          <h2>Thống kê sinh viên phụ trách</h2>
          <p>Tỷ lệ sinh viên theo trạng thái trong các lớp bạn phụ trách</p>
        </div>
        <span className="badge badge-blue">{data.sectionCount} lớp đang phụ trách</span>
      </div>
      <div className="card-body">
        <div className="chart-layout">
          <div className="chart-panel" style={{ height: 260 }}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data.overview} dataKey="value" nameKey="name" innerRadius={54} outerRadius={88} paddingAngle={3}>
                  {data.overview.map((entry) => (
                    <Cell key={entry.name} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => [`${value}`, 'Số lượng']} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="chart-legend">
            {data.overview.map((item) => (
              <div key={item.name} className="chart-legend-item">
                <span className="chart-dot" style={{ background: item.color }} />
                <div style={{ flex: 1 }}>
                  <span>{item.name}</span>
                </div>
                <strong>{item.value}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>
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

const StudentDashboard = ({ data }) => {
  const gpaTrend = Array.isArray(data?.gpaTrend) && data.gpaTrend.length > 0 ? data.gpaTrend : [];
  const passRatioData = [
    { name: 'Đạt', value: Number(data?.passedCourseCount || 0), color: '#16a34a' },
    { name: 'Không đạt', value: Number(data?.failedCourseCount || 0), color: '#f97316' },
  ].filter((item) => item.value > 0);

  const totalRequiredCredits = data?.requiredCredits ?? null;
  const completeCredits = Number(data?.completedCredits || 0);
  const totalCredits = Number(data?.totalCredits || 0);
  const progressPercent = totalRequiredCredits ? Math.min(100, (completeCredits / totalRequiredCredits) * 100) : (totalCredits ? Math.min(100, (completeCredits / Math.max(totalCredits, 1)) * 100) : 0);
  const missingCredits = totalRequiredCredits ? Math.max(0, totalRequiredCredits - completeCredits) : 0;

  return (
    <>
      <div className="stats-grid">
        <StatCard label="Học phần đã đăng ký" value={data.enrolledCount} icon={<BookIcon />} tone="blue" />
        <StatCard label="Tín chỉ tích luỹ" value={data.totalCredits} icon={<AwardIcon />} tone="green" />
        <StatCard label="GPA hiện tại" value={data.currentGpa ? Number(data.currentGpa).toFixed(2) : '—'} icon={<TrendingIcon />} tone="amber" />
        <StatCard label="Lớp đang mở" value={data.openSections} icon={<LayersIcon />} tone="indigo" />
      </div>

      <div className="stats-grid chart-stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
        <StatCard label="Môn đã hoàn thành" value={data.totalCompletedCourses} icon={<BookIcon />} tone="blue" />
        <StatCard label="Môn đạt" value={data.passedCourseCount} icon={<AwardIcon />} tone="green" />
        <StatCard label="Môn không đạt" value={data.failedCourseCount} icon={<TrendingIcon />} tone="amber" />
        <StatCard label="Tỉ lệ đạt" value={data.totalCompletedCourses ? `${data.passRate}%` : '—'} icon={<LayersIcon />} tone="indigo" />
      </div>

      <div className="split" style={{ marginBottom: 18 }}>
        <div className="card">
          <div className="card-head">
            <div>
              <h2>GPA theo học kỳ</h2>
              <p>Điểm GPA được tính từ các học phần đã được công bố</p>
            </div>
          </div>
          <div className="card-body">
            {gpaTrend.length === 0 ? (
              <EmptyState
                title="Chưa có dữ liệu GPA"
                description="Kết quả học tập sẽ xuất hiện sau khi nhà trường công bố điểm của học kỳ."
              />
            ) : (
              <div style={{ height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={gpaTrend} margin={{ top: 10, right: 10, left: -18, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                    <XAxis dataKey="semester" tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                    <YAxis domain={[0, 10]} tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                    <Tooltip
                      formatter={(value) => [`${Number(value).toFixed(2)}`, 'GPA']}
                      contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', boxShadow: 'var(--shadow-sm)' }}
                    />
                    <Line type="monotone" dataKey="gpa" stroke="#2563eb" strokeWidth={3} dot={{ r: 5, fill: '#2563eb', strokeWidth: 2, stroke: '#fff' }} activeDot={{ r: 7 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <div>
              <h2>Tỷ lệ môn đạt và không đạt</h2>
              <p>Đánh giá theo học phần đã có điểm</p>
            </div>
          </div>
          <div className="card-body">
            {data.totalCompletedCourses === 0 ? (
              <EmptyState title="Chưa có điểm môn học" description="Các môn học sẽ xuất hiện sau khi có điểm công bố trên hệ thống." />
            ) : (
              <div className="chart-layout" style={{ gridTemplateColumns: 'minmax(0, 1.1fr) minmax(200px, 0.9fr)' }}>
                <div style={{ height: 220 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={passRatioData.length ? passRatioData : [{ name: 'Không có dữ liệu', value: 1, color: '#e2e8f0' }]} dataKey="value" nameKey="name" innerRadius={52} outerRadius={82} paddingAngle={3}>
                        {(passRatioData.length ? passRatioData : [{ name: 'Không có dữ liệu', value: 1, color: '#e2e8f0' }]).map((entry) => (
                          <Cell key={entry.name} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => [`${value}`, 'Số môn']} contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0' }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="chart-legend">
                  {(passRatioData.length ? passRatioData : [{ name: 'Không có dữ liệu', value: 0, color: '#e2e8f0' }]).map((item) => (
                    <div key={item.name} className="chart-legend-item">
                      <span className="chart-dot" style={{ background: item.color }} />
                      <div style={{ flex: 1 }}>
                        <span>{item.name}</span>
                      </div>
                      <strong>{item.value}</strong>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="split">
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Tín chỉ tích lũy</h2>
              <p>Tiến độ hoàn thành theo chương trình học</p>
            </div>
          </div>
          <div className="card-body">
            <div style={{ display: 'grid', gap: 18 }}>
              <div className="flex-between">
                <span className="small muted">Đã tích lũy</span>
                <strong>{completeCredits} / {totalRequiredCredits ?? totalCredits} tín chỉ</strong>
              </div>
              <div style={{ height: 16, background: '#eef2f7', borderRadius: 999, overflow: 'hidden' }}>
                <div style={{ width: `${Math.max(0, Math.min(100, progressPercent))}%`, height: '100%', background: 'linear-gradient(90deg, #22c55e, #16a34a)', borderRadius: 999 }} />
              </div>
              <div className="flex-between small muted">
                <span>Tiến độ hoàn thành</span>
                <strong style={{ color: 'var(--text)' }}>{Math.round(progressPercent)}%</strong>
              </div>
              <div className="info-row"><span>Tín chỉ còn thiếu</span><strong>{missingCredits}</strong></div>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <div><h2>Tổng quan kết quả học tập</h2><p>Những chỉ số quan trọng nhất của bạn</p></div>
          </div>
          <div className="card-body" style={{ display: 'grid', gap: 12 }}>
            <div className="info-row"><span>GPA hiện tại</span><strong>{data.currentGpa ? Number(data.currentGpa).toFixed(2) : '—'}</strong></div>
            <div className="info-row"><span>Tổng số tín chỉ tích lũy</span><strong>{data.totalCredits}</strong></div>
            <div className="info-row"><span>Tổng số môn đã hoàn thành</span><strong>{data.totalCompletedCourses}</strong></div>
            <div className="info-row"><span>Số môn chưa đạt</span><strong>{data.failedCourseCount}</strong></div>
          </div>
        </div>
      </div>

      <div className="split" style={{ marginTop: 18 }}>
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
              <Link to="/student/timetable" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><CalendarIcon size={17} /> Thời khóa biểu</Link>
              <Link to="/student/grades" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><AwardIcon size={17} /> Kết quả học tập</Link>
              <Link to="/student/profile" className="btn btn-secondary" style={{ justifyContent: 'flex-start' }}><UserIcon size={17} /> Hồ sơ cá nhân</Link>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default Dashboard;
