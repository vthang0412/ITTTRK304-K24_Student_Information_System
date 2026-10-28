import React, { useEffect, useMemo, useState } from 'react';
import { lecturerApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { BuildingIcon, PencilIcon, PlusIcon, UserIcon, UsersIcon } from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = { lecturerCode: '', fullName: '', email: '', department: '', status: 'ACTIVE' };

const AdminLecturers = () => {
  const toast = useToast();
  const [lecturers, setLecturers] = useState([]);
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
      const res = await lecturerApi.getAll();
      setLecturers(res.data || []);
    } catch {
      toast.error('Không tải được danh sách giảng viên', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return lecturers.filter((l) => {
      if (status !== 'ALL' && l.status !== status) return false;
      if (!keyword) return true;
      return [l.lecturerCode, l.fullName, l.email, l.department]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [lecturers, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const departments = new Set(lecturers.map((l) => l.department).filter(Boolean)).size;

  const openCreate = () => {
    setEditing(null); setForm(EMPTY); setErrors({}); setOpen(true);
  };

  const openEdit = (lecturer) => {
    setEditing(lecturer);
    setForm({
      lecturerCode: lecturer.lecturerCode || '',
      fullName: lecturer.fullName || '',
      email: lecturer.email || '',
      department: lecturer.department || '',
      status: lecturer.status || 'ACTIVE',
    });
    setErrors({});
    setOpen(true);
  };

  const validate = () => {
    const next = {};
    if (!editing && !form.lecturerCode.trim()) next.lecturerCode = 'Mã giảng viên là bắt buộc.';
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
        lecturerCode: form.lecturerCode.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        department: form.department.trim(),
        status: form.status,
      };
      if (editing) {
        await lecturerApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật giảng viên ${payload.fullName}.`);
      } else {
        await lecturerApi.create(payload);
        toast.success('Thêm giảng viên thành công', `${payload.fullName} đã được thêm vào hệ thống.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu giảng viên', getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý giảng viên"
        subtitle="Quản lý hồ sơ giảng viên và trạng thái công tác."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm giảng viên</button>}
      />

      <div className="stats-grid">
        <StatCard label="Tổng giảng viên" value={lecturers.length} icon={<UsersIcon />} tone="blue" />
        <StatCard label="Đang hoạt động" value={lecturers.filter((l) => l.status === 'ACTIVE').length} icon={<UserIcon />} tone="green" />
        <StatCard label="Ngừng hoạt động" value={lecturers.filter((l) => l.status === 'INACTIVE').length} icon={<UserIcon />} tone="red" />
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
        <span className="chip">{filtered.length} giảng viên</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<UsersIcon />}
            title="Chưa có giảng viên"
            description="Thêm giảng viên mới hoặc điều chỉnh bộ lọc để xem dữ liệu."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm giảng viên</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã giảng viên</th>
                    <th>Họ và tên</th>
                    <th>Email</th>
                    <th>Khoa</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((l) => (
                    <tr key={l.id}>
                      <td className="cell-strong">{l.lecturerCode}</td>
                      <td>{l.fullName}</td>
                      <td className="cell-muted">{l.email}</td>
                      <td>{l.department || '—'}</td>
                      <td><StatusBadge status={l.status} /></td>
                      <td>
                        <div className="row-actions">
                          <button className="btn btn-secondary btn-sm" onClick={() => openEdit(l)}><PencilIcon size={14} /> Sửa</button>
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
        title={editing ? 'Cập nhật giảng viên' : 'Thêm giảng viên mới'}
        subtitle={editing ? `Mã giảng viên: ${editing.lecturerCode}` : 'Tạo hồ sơ giảng viên mới trong hệ thống.'}
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu giảng viên'}</button>
          </>
        )}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            <Field label="Mã giảng viên" required error={errors.lecturerCode}>
              <input className={`input${errors.lecturerCode ? ' invalid' : ''}`} value={form.lecturerCode} onChange={(e) => setForm({ ...form, lecturerCode: e.target.value })} disabled={!!editing} placeholder="VD: GV001" />
            </Field>
            <Field label="Họ và tên" required error={errors.fullName}>
              <input className={`input${errors.fullName ? ' invalid' : ''}`} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="Nguyễn Văn A" />
            </Field>
            <Field label="Email" required error={errors.email}>
              <input className={`input${errors.email ? ' invalid' : ''}`} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="giangvien@example.com" />
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

export default AdminLecturers;
