import React, { useEffect, useMemo, useState } from 'react';
import { authApi } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { cohortService } from '../services/dashboardService';
import {
  Field, LoadingBlock, Modal, PageHeader, StatCard, StatusBadge,
} from '../components/UI';
import {
  AwardIcon, BookIcon, BuildingIcon, CapIcon, LayersIcon, LockIcon, MailIcon,
  PencilIcon, PinIcon, SaveIcon, ShieldIcon, UserIcon, UsersIcon,
} from '../components/Icons';
import { formatScore } from '../utils/format';

const ROLE_META = {
  STUDENT: { label: 'Sinh viên', icon: CapIcon, codeLabel: 'Mã sinh viên' },
  LECTURER: { label: 'Giảng viên', icon: UserIcon, codeLabel: 'Mã giảng viên' },
  ADMIN: { label: 'Quản trị viên', icon: ShieldIcon, codeLabel: 'Mã tài khoản' },
};

const InfoRow = ({ label, value }) => (
  <div className="info-row">
    <span>{label}</span>
    <strong>{value || '—'}</strong>
  </div>
);

const emptyForm = {
  fullName: '',
  email: '',
  department: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
};

const Profile = () => {
  const { user, updateUser } = useAuth();
  const toast = useToast();
  const [profile, setProfile] = useState(null);
  const [cohort, setCohort] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});

  const load = async () => {
    try {
      const meRes = await authApi.me();
      const me = meRes.data || {};
      const currentRole = me.role || user?.role || 'STUDENT';
      let cohortData = null;
      try {
        cohortData = await cohortService.load(currentRole);
      } catch {
        cohortData = null;
      }
      setProfile(me);
      setCohort(cohortData);
      updateUser({
        fullName: me.fullName,
        email: me.email,
        studentId: me.studentId,
        studentCode: me.studentCode,
        lecturerId: me.lecturerId,
        lecturerCode: me.lecturerCode,
        department: me.department,
        role: me.role,
      });
    } catch {
      toast.error('Không tải được hồ sơ', 'Vui lòng kiểm tra kết nối và thử lại.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const currentRole = profile?.role || user?.role || 'STUDENT';
  const meta = ROLE_META[currentRole] || ROLE_META.STUDENT;
  const displayName = profile?.fullName || user?.fullName || user?.username || 'Người dùng';
  const initials = useMemo(
    () => displayName.trim().split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase(),
    [displayName],
  );
  const profileCode = profile?.studentCode || profile?.lecturerCode;

  const stats = useMemo(() => {
    if (!cohort) return [];
    if (currentRole === 'ADMIN') {
      return [
        { label: 'Sinh viên', value: cohort.totalStudents ?? 0, icon: <UsersIcon />, tone: 'blue' },
        { label: 'Giảng viên', value: cohort.totalLecturers ?? 0, icon: <UserIcon />, tone: 'green' },
        { label: 'Lớp học phần', value: cohort.totalSections ?? 0, icon: <LayersIcon />, tone: 'amber' },
      ];
    }
    if (currentRole === 'LECTURER') {
      return [
        { label: 'Lớp phụ trách', value: cohort.sectionCount ?? 0, icon: <LayersIcon />, tone: 'blue' },
        { label: 'Lớp đang mở', value: cohort.openCount ?? 0, icon: <BookIcon />, tone: 'green' },
        { label: 'Sinh viên đã đăng ký', value: cohort.enrolledCount ?? 0, icon: <UsersIcon />, tone: 'amber' },
      ];
    }
    return [
      { label: 'Học phần đang học', value: cohort.enrolledCount ?? 0, icon: <BookIcon />, tone: 'blue' },
      { label: 'Tín chỉ tích lũy', value: cohort.totalCredits ?? 0, icon: <AwardIcon />, tone: 'green' },
      { label: 'Điểm trung bình', value: formatScore(cohort.averageScore), icon: <CapIcon />, tone: 'amber' },
    ];
  }, [cohort, currentRole]);

  const openEdit = () => {
    setForm({
      fullName: profile?.fullName || '',
      email: profile?.email || '',
      department: profile?.department || '',
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    setErrors({});
    setEditing(true);
  };

  const setField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: undefined, general: undefined }));
  };

  const validate = () => {
    const next = {};
    if (!form.fullName.trim()) next.fullName = 'Vui lòng nhập họ tên.';
    if (!form.email.trim()) next.email = 'Vui lòng nhập email.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Email không hợp lệ.';
    if (form.newPassword || form.currentPassword || form.confirmPassword) {
      if (!form.currentPassword) next.currentPassword = 'Vui lòng nhập mật khẩu hiện tại.';
      if (!form.newPassword || form.newPassword.length < 6) next.newPassword = 'Mật khẩu mới phải có ít nhất 6 ký tự.';
      if (form.newPassword !== form.confirmPassword) next.confirmPassword = 'Mật khẩu xác nhận không khớp.';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = async (event) => {
    event.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        department: currentRole === 'ADMIN' ? null : form.department.trim(),
      };
      if (form.newPassword) {
        payload.currentPassword = form.currentPassword;
        payload.newPassword = form.newPassword;
      }
      const res = await authApi.updateProfile(payload);
      const me = res.data || {};
      setProfile(me);
      updateUser({
        fullName: me.fullName,
        email: me.email,
        department: me.department,
        role: me.role,
        studentCode: me.studentCode,
        lecturerCode: me.lecturerCode,
        studentId: me.studentId,
        lecturerId: me.lecturerId,
      });
      setEditing(false);
      toast.success('Cập nhật hồ sơ thành công', 'Thông tin tài khoản đã được lưu.');
    } catch (err) {
      const message = err.response?.data?.message || 'Không thể cập nhật hồ sơ.';
      setErrors({ general: message });
      toast.error('Cập nhật thất bại', message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Hồ sơ cá nhân"
        subtitle="Quản lý thông tin tài khoản của bạn."
        actions={(
          <button className="btn btn-primary" onClick={openEdit}>
            <PencilIcon size={16} /> Chỉnh sửa hồ sơ
          </button>
        )}
      />

      {loading ? (
        <div className="card"><LoadingBlock /></div>
      ) : (
        <>
          {stats.length > 0 && (
            <div className="stats-grid">
              {stats.map((item) => (
                <StatCard key={item.label} label={item.label} value={item.value} icon={item.icon} tone={item.tone} />
              ))}
            </div>
          )}

          <div className="split">
            <div className="card">
              <div className="card-head">
                <h2>Thông tin cá nhân</h2>
                <span className="chip">{meta.label}</span>
              </div>
              <div className="card-body">
                <div className="profile-head">
                  <div className="avatar avatar-lg">{initials}</div>
                  <div>
                    <h2>{displayName}</h2>
                    <p>{profileCode || 'Chưa có mã định danh'}</p>
                    <div style={{ marginTop: 8 }}><StatusBadge status={profile?.status || 'ACTIVE'} /></div>
                  </div>
                </div>
                <div className="info-list">
                  <InfoRow label="Tên đăng nhập" value={profile?.username || user?.username} />
                  <InfoRow label="Email" value={profile?.email || user?.email} />
                  <InfoRow label={meta.codeLabel} value={profileCode} />
                  <InfoRow label="Khoa / Bộ môn" value={profile?.department || user?.department} />
                  <InfoRow label="Vai trò" value={meta.label} />
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
                      <div className="small muted">Khoa / Bộ môn</div>
                      <strong className="small">{profile?.department || user?.department || '—'}</strong>
                    </div>
                  </div>
                  <div className="flex" style={{ gap: 12 }}>
                    <div className="stat-icon tone-amber" style={{ width: 38, height: 38, flexBasis: 38 }}><BuildingIcon size={18} /></div>
                    <div>
                      <div className="small muted">Trạng thái tài khoản</div>
                      <strong className="small">{profile?.status === 'INACTIVE' ? 'Ngừng hoạt động' : 'Đang hoạt động'}</strong>
                    </div>
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-head"><h2>Bảo mật</h2></div>
                <div className="card-body" style={{ display: 'grid', gap: 12 }}>
                  <div className="flex" style={{ gap: 12 }}>
                    <div className="stat-icon tone-blue" style={{ width: 38, height: 38, flexBasis: 38 }}><LockIcon size={18} /></div>
                    <div>
                      <div className="small muted">Mật khẩu</div>
                      <strong className="small">Thay đổi mật khẩu bằng cách chỉnh sửa hồ sơ</strong>
                    </div>
                  </div>
                  <button className="btn btn-secondary btn-block" onClick={openEdit}>Chỉnh sửa thông tin / đổi mật khẩu</button>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      <Modal
        open={editing}
        onClose={saving ? () => {} : () => setEditing(false)}
        title="Chỉnh sửa hồ sơ"
        subtitle="Cập nhật họ tên, email, khoa/bộ môn hoặc đổi mật khẩu."
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setEditing(false)} disabled={saving}>Hủy</button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? 'Đang lưu...' : <><SaveIcon size={16} /> Lưu thay đổi</>}
            </button>
          </>
        )}
      >
        <form onSubmit={handleSave} style={{ display: 'grid', gap: 16 }}>
          {errors.general && <div className="alert alert-error">{errors.general}</div>}
          <Field label="Họ và tên" required error={errors.fullName}>
            <input className="input" value={form.fullName} onChange={(e) => setField('fullName', e.target.value)} />
          </Field>
          <Field label="Email" required error={errors.email}>
            <input className="input" type="email" value={form.email} onChange={(e) => setField('email', e.target.value)} />
          </Field>
          {currentRole !== 'ADMIN' && (
            <Field label="Khoa / Bộ môn" error={errors.department}>
              <input className="input" value={form.department} onChange={(e) => setField('department', e.target.value)} />
            </Field>
          )}
          <div style={{ height: 1, background: 'var(--border)' }} />
          <p className="small muted" style={{ margin: 0 }}>Để trống nếu không đổi mật khẩu.</p>
          <Field label="Mật khẩu hiện tại" error={errors.currentPassword}>
            <input className="input" type="password" autoComplete="current-password" value={form.currentPassword} onChange={(e) => setField('currentPassword', e.target.value)} />
          </Field>
          <Field label="Mật khẩu mới" error={errors.newPassword} hint="Tối thiểu 6 ký tự">
            <input className="input" type="password" autoComplete="new-password" value={form.newPassword} onChange={(e) => setField('newPassword', e.target.value)} />
          </Field>
          <Field label="Xác nhận mật khẩu mới" error={errors.confirmPassword}>
            <input className="input" type="password" autoComplete="new-password" value={form.confirmPassword} onChange={(e) => setField('confirmPassword', e.target.value)} />
          </Field>
        </form>
      </Modal>
    </>
  );
};

export default Profile;
