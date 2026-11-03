import React, { useEffect, useState } from 'react';
import { authApi, enrollmentApi, gradeApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { LoadingBlock, PageHeader, StatCard, StatusBadge } from '../../components/UI';
import { AwardIcon, BookIcon, MailIcon, PinIcon, UserIcon, UsersIcon } from '../../components/Icons';
import { formatScore } from '../../utils/format';

const ProfileRow = ({ label, value }) => (
  <div className="info-row">
    <span>{label}</span>
    <strong>{value || '—'}</strong>
  </div>
);

const StudentProfile = () => {
  const { user, updateUser } = useAuth();
  const toast = useToast();
  const [profile, setProfile] = useState(null);
  const [stats, setStats] = useState({ enrollments: 0, credits: 0, average: '—' });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        const [meRes, enrollRes, gradeRes] = await Promise.all([
          authApi.me(),
          enrollmentApi.getMyEnrollments(),
          gradeApi.getMyGrades().catch(() => ({ data: [] })),
        ]);
        if (!mounted) return;
        const me = meRes.data || {};
        setProfile(me);
        updateUser({ fullName: me.fullName, email: me.email, studentId: me.studentId, studentCode: me.studentCode, department: me.department });

        const active = (enrollRes.data || []).filter((e) => e.status === 'ENROLLED');
        const grades = gradeRes.data || [];
        const merged = active.map((e) => ({ ...e, grade: grades.find((g) => g.id === e.id)?.grade ?? e.grade }));
        const credits = merged.reduce((sum, e) => sum + Number(e.courseSection?.course?.credits || 0), 0);
        const scores = merged.map((e) => e.grade?.finalScore).filter((v) => v !== null && v !== undefined && v !== '');
        const avg = scores.length ? (scores.reduce((s, v) => s + Number(v), 0) / scores.length).toFixed(2) : '—';
        setStats({ enrollments: active.length, credits, average: avg });
      } catch {
        if (mounted) toast.error('Không tải được hồ sơ', 'Vui lòng thử lại sau.');
      } finally {
        if (mounted) setLoading(false);
      }
    };
    load();
    return () => { mounted = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const displayName = profile?.fullName || user?.fullName || user?.username;
  const initials = (displayName || 'U').trim().split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase();

  return (
    <>
      <PageHeader title="Hồ sơ cá nhân" subtitle="Thông tin tài khoản và quá trình học tập của bạn." />

      {loading ? (
        <div className="card"><LoadingBlock /></div>
      ) : (
        <>
          <div className="stats-grid">
            <StatCard label="Học phần đang học" value={stats.enrollments} icon={<BookIcon />} tone="blue" />
            <StatCard label="Tín chỉ tích luỹ" value={stats.credits} icon={<AwardIcon />} tone="green" />
            <StatCard label="Điểm trung bình" value={stats.average} icon={<UsersIcon />} tone="amber" />
          </div>

          <div className="split">
            <div className="card">
              <div className="card-head"><h2>Thông tin cá nhân</h2><span className="chip">Tài khoản sinh viên</span></div>
              <div className="card-body">
                <div className="profile-head">
                  <div className="avatar avatar-lg">{initials}</div>
                  <div>
                    <h2>{displayName}</h2>
                    <p>{profile?.studentCode || user?.studentCode || 'Chưa có mã sinh viên'}</p>
                    <div style={{ marginTop: 8 }}><StatusBadge status={profile?.status || 'ACTIVE'} /></div>
                  </div>
                </div>
                <div className="info-list">
                  <ProfileRow label="Tên đăng nhập" value={profile?.username || user?.username} />
                  <ProfileRow label="Email" value={profile?.email || user?.email} />
                  <ProfileRow label="Khoa / Ngành" value={profile?.department || '—'} />
                  <ProfileRow label="Mã sinh viên" value={profile?.studentCode} />
                  <ProfileRow label="Vai trò" value="Sinh viên" />
                </div>
              </div>
            </div>

            <div className="stack">
              <div className="card">
                <div className="card-head"><h2>Liên hệ</h2></div>
                <div className="card-body" style={{ display: 'grid', gap: 14 }}>
                  <div className="flex" style={{ gap: 12 }}>
                    <div className="stat-icon tone-blue" style={{ width: 38, height: 38, flexBasis: 38 }}><MailIcon size={18} /></div>
                    <div>
                      <div className="small muted">Email</div>
                      <strong className="small">{profile?.email || user?.email || '—'}</strong>
                    </div>
                  </div>
                  <div className="flex" style={{ gap: 12 }}>
                    <div className="stat-icon tone-green" style={{ width: 38, height: 38, flexBasis: 38 }}><PinIcon size={18} /></div>
                    <div>
                      <div className="small muted">Khoa</div>
                      <strong className="small">{profile?.department || '—'}</strong>
                    </div>
                  </div>
                  <div className="flex" style={{ gap: 12 }}>
                    <div className="stat-icon tone-amber" style={{ width: 38, height: 38, flexBasis: 38 }}><UserIcon size={18} /></div>
                    <div>
                      <div className="small muted">Trạng thái tài khoản</div>
                      <strong className="small">{profile?.status === 'INACTIVE' ? 'Ngừng hoạt động' : 'Đang hoạt động'}</strong>
                    </div>
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-head"><h2>Kết quả học tập</h2></div>
                <div className="card-body">
                  <div className="info-row"><span>Học phần đang học</span><strong>{stats.enrollments}</strong></div>
                  <div className="info-row"><span>Tín chỉ</span><strong>{stats.credits}</strong></div>
                  <div className="info-row"><span>Điểm trung bình</span><strong>{formatScore(stats.average)}</strong></div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </>
  );
};

export default StudentProfile;
