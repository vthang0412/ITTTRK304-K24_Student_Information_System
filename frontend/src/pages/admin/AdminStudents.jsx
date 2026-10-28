import React, { useEffect, useMemo, useState } from 'react';
import { studentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { BuildingIcon, PencilIcon, PlusIcon, StudentsIcon, UsersIcon } from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = { studentCode: '', fullName: '', email: '', department: '', status: 'ACTIVE' };

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
    setForm(EMPTY);
    setErrors({});
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
    if (!editing && !form.studentCode.trim()) next.studentCode = 'Mã sinh viên là bắt buộc.';
    if (!form.fullName.trim()) next.fullName = 'Họ và tên là bắt buộc.';
    if (!form.email.trim()) next.email = 'Email là bắt buộc.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Email không hợp lệ.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        studentCode: form.studentCode.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        department: form.department.trim(),
        status: form.status,
      };
      if (editing) {
        await studentApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật sinh viên ${payload.fullName}.`);
      } else {
        await studentApi.create(payload);
        toast.success('Thêm sinh viên thành công', `${payload.fullName} đã được thêm vào hệ thống.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu sinh viên', getErrorMessage(err));
    } finally {
      setSaving(false);
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
        onClose={saving ? () => {} : () => setOpen(false)}
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
            <Field label="Mã sinh viên" required error={errors.studentCode}>
              <input className={`input${errors.studentCode ? ' invalid' : ''}`} value={form.studentCode} onChange={(e) => setForm({ ...form, studentCode: e.target.value })} disabled={!!editing} placeholder="VD: SV001" />
            </Field>
            <Field label="Họ và tên" required error={errors.fullName}>
              <input className={`input${errors.fullName ? ' invalid' : ''}`} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="Nguyễn Văn A" />
            </Field>
            <Field label="Email" required error={errors.email}>
              <input className={`input${errors.email ? ' invalid' : ''}`} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="sinhvien@example.com" />
            </Field>
            <Field label="Khoa / Ngành">
              <input className="input" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} placeholder="VD: Công nghệ thông tin" />
            </Field>
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
