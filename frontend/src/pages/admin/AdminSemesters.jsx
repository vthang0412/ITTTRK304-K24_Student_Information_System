import React, { useEffect, useMemo, useState } from 'react';
import { semesterApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CalendarIcon, ClockIcon, PencilIcon, PlusIcon } from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { formatDate, formatDateTime, getErrorMessage, toDateInput, toDateTimeInput } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = {
  semesterCode: '', semesterName: '', startDate: '', endDate: '',
  registrationStart: '', registrationEnd: '', status: 'PLANNED',
};

const AdminSemesters = () => {
  const toast = useToast();
  const [semesters, setSemesters] = useState([]);
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
      const res = await semesterApi.getAll();
      setSemesters(res.data || []);
    } catch {
      toast.error('Không tải được danh sách học kỳ', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return semesters.filter((s) => {
      if (status !== 'ALL' && s.status !== status) return false;
      if (!keyword) return true;
      return [s.semesterCode, s.semesterName].filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [semesters, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openCreate = () => { setEditing(null); setForm(EMPTY); setErrors({}); setOpen(true); };

  const openEdit = (semester) => {
    setEditing(semester);
    setForm({
      semesterCode: semester.semesterCode || '',
      semesterName: semester.semesterName || '',
      startDate: toDateInput(semester.startDate),
      endDate: toDateInput(semester.endDate),
      registrationStart: toDateTimeInput(semester.registrationStart),
      registrationEnd: toDateTimeInput(semester.registrationEnd),
      status: semester.status || 'PLANNED',
    });
    setErrors({});
    setOpen(true);
  };

  const validate = () => {
    const next = {};
    if (!editing && !form.semesterCode.trim()) next.semesterCode = 'Mã học kỳ là bắt buộc.';
    if (!form.semesterName.trim()) next.semesterName = 'Tên học kỳ là bắt buộc.';
    if (!form.startDate) next.startDate = 'Ngày bắt đầu là bắt buộc.';
    if (!form.endDate) next.endDate = 'Ngày kết thúc là bắt buộc.';
    if (form.startDate && form.endDate && form.endDate < form.startDate) next.endDate = 'Ngày kết thúc phải sau ngày bắt đầu.';
    if (form.registrationStart && form.registrationEnd && form.registrationEnd < form.registrationStart) {
      next.registrationEnd = 'Thời gian kết thúc đăng ký phải sau thời gian bắt đầu.';
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
        semesterCode: form.semesterCode.trim(),
        semesterName: form.semesterName.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
        registrationStart: form.registrationStart || null,
        registrationEnd: form.registrationEnd || null,
        status: form.status,
      };
      if (editing) {
        await semesterApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật học kỳ ${payload.semesterName}.`);
      } else {
        await semesterApi.create(payload);
        toast.success('Thêm học kỳ thành công', `${payload.semesterName} đã được tạo.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu học kỳ', getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý học kỳ"
        subtitle="Thiết lập học kỳ, thời gian diễn ra và thời gian đăng ký học phần."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm học kỳ</button>}
      />

      <div className="stats-grid">
        <StatCard label="Tổng học kỳ" value={semesters.length} icon={<CalendarIcon />} tone="blue" />
        <StatCard label="Đang mở đăng ký" value={semesters.filter((s) => s.status === 'REGISTRATION_OPEN').length} icon={<ClockIcon />} tone="green" />
        <StatCard label="Đang diễn ra" value={semesters.filter((s) => s.status === 'IN_PROGRESS').length} icon={<CalendarIcon />} tone="amber" />
        <StatCard label="Đã kết thúc" value={semesters.filter((s) => s.status === 'COMPLETED').length} icon={<ClockIcon />} tone="indigo" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo mã hoặc tên học kỳ..." />
        <select className="select" style={{ width: 210 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="PLANNED">Sắp diễn ra</option>
          <option value="REGISTRATION_OPEN">Đang mở đăng ký</option>
          <option value="IN_PROGRESS">Đang diễn ra</option>
          <option value="COMPLETED">Đã kết thúc</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} học kỳ</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<CalendarIcon />}
            title="Chưa có học kỳ"
            description="Tạo học kỳ mới hoặc điều chỉnh bộ lọc để xem dữ liệu."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm học kỳ</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã học kỳ</th>
                    <th>Tên học kỳ</th>
                    <th>Thời gian</th>
                    <th>Đăng ký</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) => (
                    <tr key={s.id}>
                      <td className="cell-strong">{s.semesterCode}</td>
                      <td>{s.semesterName}</td>
                      <td className="cell-muted">{formatDate(s.startDate)} – {formatDate(s.endDate)}</td>
                      <td className="cell-muted">
                        {s.registrationStart || s.registrationEnd
                          ? `${formatDateTime(s.registrationStart)} – ${formatDateTime(s.registrationEnd)}`
                          : '—'}
                      </td>
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
        title={editing ? 'Cập nhật học kỳ' : 'Thêm học kỳ mới'}
        subtitle={editing ? `Mã học kỳ: ${editing.semesterCode}` : 'Thiết lập học kỳ và thời gian đăng ký.'}
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu học kỳ'}</button>
          </>
        )}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            <Field label="Mã học kỳ" required error={errors.semesterCode}>
              <input className={`input${errors.semesterCode ? ' invalid' : ''}`} value={form.semesterCode} onChange={(e) => setForm({ ...form, semesterCode: e.target.value })} disabled={!!editing} placeholder="VD: HK1-2025" />
            </Field>
            <Field label="Tên học kỳ" required error={errors.semesterName}>
              <input className={`input${errors.semesterName ? ' invalid' : ''}`} value={form.semesterName} onChange={(e) => setForm({ ...form, semesterName: e.target.value })} placeholder="VD: Học kỳ 1 năm 2025-2026" />
            </Field>
            <Field label="Ngày bắt đầu" required error={errors.startDate}>
              <input className={`input${errors.startDate ? ' invalid' : ''}`} type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            </Field>
            <Field label="Ngày kết thúc" required error={errors.endDate}>
              <input className={`input${errors.endDate ? ' invalid' : ''}`} type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
            </Field>
            <Field label="Bắt đầu đăng ký" error={errors.registrationStart}>
              <input className="input" type="datetime-local" value={form.registrationStart} onChange={(e) => setForm({ ...form, registrationStart: e.target.value })} />
            </Field>
            <Field label="Kết thúc đăng ký" error={errors.registrationEnd}>
              <input className={`input${errors.registrationEnd ? ' invalid' : ''}`} type="datetime-local" value={form.registrationEnd} onChange={(e) => setForm({ ...form, registrationEnd: e.target.value })} />
            </Field>
            <Field label="Trạng thái" className="full">
              <select className="select" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="PLANNED">Sắp diễn ra</option>
                <option value="REGISTRATION_OPEN">Đang mở đăng ký</option>
                <option value="IN_PROGRESS">Đang diễn ra</option>
                <option value="COMPLETED">Đã kết thúc</option>
              </select>
            </Field>
          </div>
        </form>
      </Modal>
    </>
  );
};

export default AdminSemesters;
