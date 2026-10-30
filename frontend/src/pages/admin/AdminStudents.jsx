import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { authApi, studentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { BuildingIcon, EyeIcon, EyeOffIcon, PencilIcon, PlusIcon, StudentsIcon, TrashIcon, UsersIcon } from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = { fullName: '', email: '', department: '', status: 'ACTIVE', password: '', confirmPassword: '' };

const generatePassword = () => {
  const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';
  const specials = '!@#$%';
  const chars = `${letters}${digits}${specials}`;
  const pick = (source) => source[Math.floor(Math.random() * source.length)];
  let password = '';
  password += pick(letters.toUpperCase());
  password += pick(letters.toLowerCase());
  password += pick(digits);
  password += pick(specials);
  for (let i = 4; i < 12; i += 1) password += pick(chars);
  return password.split('').sort(() => Math.random() - 0.5).join('');
};

const createUsernameFromProfile = (fullName, studentCode) => {
  const base = `${fullName || ''} ${studentCode || ''}`
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '')
    .slice(0, 20);

  return base || `student${Math.random().toString(36).slice(2, 8)}`;
};

const AdminStudents = () => {
  const toast = useToast();
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const load = async () => {
    try {
      const res = await studentApi.getAll();
      setStudents(res.data || []);
    } catch {
      toast.error('Không tải được danh sách sinh viên', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return students.filter((s) => {
      if (status !== 'ALL' && s.status !== status) return false;
      if (!keyword) return true;
      return [s.studentCode, s.fullName, s.email, s.department]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [students, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const departments = new Set(students.map((s) => s.department).filter(Boolean)).size;

  const openCreate = () => {
    setEditing(null);
    const password = generatePassword();
    setForm({ ...EMPTY, password, confirmPassword: password });
    setErrors({});
    setShowPassword(false);
    setShowConfirmPassword(false);
    setOpen(true);
  };

  const openEdit = (student) => {
    setEditing(student);
    setForm({
      studentCode: student.studentCode || '',
      fullName: student.fullName || '',
      email: student.email || '',
      department: student.department || '',
      status: student.status || 'ACTIVE',
    });
    setErrors({});
    setOpen(true);
  };

  const validate = () => {
    const next = {};
    if (!form.fullName.trim()) next.fullName = 'Họ và tên là bắt buộc.';
    if (!form.email.trim()) next.email = 'Email là bắt buộc.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Email không hợp lệ.';

    if (!editing) {
      if (!form.password) next.password = 'Mật khẩu là bắt buộc.';
      else if (form.password.length < 6) next.password = 'Mật khẩu phải có ít nhất 6 ký tự.';
      if (!form.confirmPassword) next.confirmPassword = 'Vui lòng xác nhận mật khẩu.';
      else if (form.password !== form.confirmPassword) next.confirmPassword = 'Mật khẩu xác nhận không khớp.';
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        department: form.department.trim(),
        status: form.status,
        ...(editing ? { studentCode: form.studentCode.trim() } : {}),
      };

      if (editing) {
        await studentApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật sinh viên ${payload.fullName}.`);
      } else {
        const usernameBase = createUsernameFromProfile(payload.fullName, 'SV' + Math.random().toString().slice(2, 10));
        let username = usernameBase;
        let attempt = 0;
        while (attempt < 5) {
          try {
            await authApi.register({
              fullName: payload.fullName,
              username,
              email: payload.email,
              password: form.password,
              role: 'STUDENT',
            });
            break;
          } catch (err) {
            const message = getErrorMessage(err).toLowerCase();
            if (!message.includes('username already exists') && !message.includes('email already exists')) {
              throw err;
            }
            username = `${usernameBase}${Math.random().toString(36).slice(2, 6)}`;
            attempt += 1;
          }
        }

        if (attempt >= 5) {
          throw new Error('Không thể tạo tên đăng nhập duy nhất cho tài khoản này.');
        }

        toast.success('Tạo tài khoản thành công', `${payload.fullName} đã được tạo trong hệ thống với mật khẩu khởi tạo đã được thiết lập.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu sinh viên', getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (student) => {
    const result = await Swal.fire({
      title: 'Xác nhận xoá sinh viên?',
      text: `Bạn có chắc muốn xoá ${student.fullName || student.studentCode}? Hành động này sẽ không hoàn tác nếu backend đã hỗ trợ API xoá.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Xoá sinh viên',
      cancelButtonText: 'Huỷ',
      reverseButtons: true,
      customClass: {
        popup: 'swal-auth-popup',
        title: 'swal-auth-title',
        htmlContainer: 'swal-auth-text',
        confirmButton: 'swal-auth-confirm',
        cancelButton: 'swal-auth-cancel',
        icon: 'swal-auth-icon',
      },
      buttonsStyling: false,
      background: '#ffffff',
      color: '#0f172a',
      width: 440,
    });

    if (!result.isConfirmed) return;

    try {
      await studentApi.delete(student.id);
      toast.success('Xoá sinh viên thành công', `Đã xoá ${student.fullName || student.studentCode}.`);
      load();
    } catch (err) {
      const status = err?.response?.status;
      if (status === 404 || status === 405) {
        toast.error('Chưa hỗ trợ xoá trên backend', 'API xoá sinh viên chưa được triển khai trên server. Hãy bổ sung endpoint DELETE /api/students/{id} trước khi dùng chức năng này.');
      } else {
        toast.error('Không thể xoá sinh viên', getErrorMessage(err));
      }
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý sinh viên"
        subtitle="Quản lý hồ sơ, khoa/ngành và trạng thái học tập của sinh viên."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm sinh viên</button>}
      />

      <div className="stats-grid">
        <StatCard label="Tổng sinh viên" value={students.length} icon={<StudentsIcon />} tone="blue" />
        <StatCard label="Đang hoạt động" value={students.filter((s) => s.status === 'ACTIVE').length} icon={<UsersIcon />} tone="green" />
        <StatCard label="Ngừng hoạt động" value={students.filter((s) => s.status === 'INACTIVE').length} icon={<UsersIcon />} tone="red" />
        <StatCard label="Số khoa" value={departments} icon={<BuildingIcon />} tone="amber" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo mã, tên, email, khoa..." />
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Ngừng hoạt động</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} sinh viên</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<StudentsIcon />}
            title="Chưa có sinh viên"
            description="Thêm sinh viên mới hoặc điều chỉnh bộ lọc để xem dữ liệu."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm sinh viên</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã sinh viên</th>
                    <th>Họ và tên</th>
                    <th>Email</th>
                    <th>Khoa</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) => (
                    <tr key={s.id}>
                      <td className="cell-strong">{s.studentCode}</td>
                      <td>{s.fullName}</td>
                      <td className="cell-muted">{s.email}</td>
                      <td>{s.department || '—'}</td>
                      <td><StatusBadge status={s.status} /></td>
                      <td>
                        <div className="row-actions">
                          <button className="btn btn-secondary btn-sm" onClick={() => openEdit(s)}><PencilIcon size={14} /> Sửa</button>
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => handleDelete(s)}
                            disabled={s.status === 'ACTIVE'}
                            style={{ opacity: s.status === 'ACTIVE' ? 0.45 : 1, cursor: s.status === 'ACTIVE' ? 'not-allowed' : 'pointer' }}
                          >
                            <TrashIcon size={14} /> Xoá
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} pageSize={PAGE_SIZE} totalItems={filtered.length} onPageChange={setPage} />
          </>
        )}
      </div>

      <Modal
        open={open}
        onClose={saving ? () => { } : () => setOpen(false)}
        title={editing ? 'Cập nhật sinh viên' : 'Thêm sinh viên mới'}
        subtitle={editing ? `Mã sinh viên: ${editing.studentCode}` : 'Tạo hồ sơ sinh viên mới trong hệ thống.'}
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu sinh viên'}</button>
          </>
        )}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            {editing && (
              <Field label="Mã sinh viên" error={errors.studentCode}>
                <input className={`input${errors.studentCode ? ' invalid' : ''}`} value={form.studentCode || ''} onChange={(e) => setForm({ ...form, studentCode: e.target.value })} disabled={!!editing} placeholder="VD: SV001" />
              </Field>
            )}
            <Field label="Họ và tên" required error={errors.fullName}>
              <input className={`input${errors.fullName ? ' invalid' : ''}`} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="Nguyễn Văn A" />
            </Field>
            <Field label="Email" required error={errors.email}>
              <input className={`input${errors.email ? ' invalid' : ''}`} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="sinhvien@example.com" />
            </Field>
            <Field label="Khoa / Ngành">
              <input className="input" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} placeholder="VD: Công nghệ thông tin" />
            </Field>
            {!editing && (
              <>
                <Field label="Mật khẩu" required error={errors.password} hint="Tối thiểu 6 ký tự">
                  <div style={{ position: 'relative' }}>
                    <input
                      className={`input${errors.password ? ' invalid' : ''}`}
                      type={showPassword ? 'text' : 'password'}
                      value={form.password}
                      onChange={(e) => setForm({ ...form, password: e.target.value })}
                      autoComplete="new-password"
                      placeholder="Nhập mật khẩu khởi tạo"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      style={{ position: 'absolute', right: 8, top: 9, border: 'none', background: 'transparent', color: 'var(--muted)' }}
                    >
                      {showPassword ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  </div>
                </Field>

                <Field label="Xác nhận mật khẩu" required error={errors.confirmPassword}>
                  <div style={{ position: 'relative' }}>
                    <input
                      className={`input${errors.confirmPassword ? ' invalid' : ''}`}
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={form.confirmPassword}
                      onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                      autoComplete="new-password"
                      placeholder="Nhập lại mật khẩu"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword((v) => !v)}
                      aria-label={showConfirmPassword ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                      style={{ position: 'absolute', right: 8, top: 9, border: 'none', background: 'transparent', color: 'var(--muted)' }}
                    >
                      {showConfirmPassword ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  </div>
                </Field>

                <div className="full" style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 2 }}>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => {
                      const nextPassword = generatePassword();
                      setForm((prev) => ({ ...prev, password: nextPassword, confirmPassword: nextPassword }));
                    }}
                  >
                    Tạo mật khẩu ngẫu nhiên
                  </button>
                </div>
              </>
            )}

            <Field label="Trạng thái" className="full">
              <select className="select" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="ACTIVE">Đang hoạt động</option>
                <option value="INACTIVE">Ngừng hoạt động</option>
              </select>
            </Field>
          </div>
        </form>
      </Modal>
    </>
  );
};

export default AdminStudents;
