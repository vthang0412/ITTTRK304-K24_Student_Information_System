import React, { useEffect, useMemo, useState } from 'react';
import { courseApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { AwardIcon, BookIcon, LayersIcon, PencilIcon, PlusIcon } from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = { courseCode: '', courseName: '', credits: 3, description: '', status: 'ACTIVE' };

const AdminCourses = () => {
  const toast = useToast();
  const [courses, setCourses] = useState([]);
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
      const res = await courseApi.getAll();
      setCourses(res.data || []);
    } catch {
      toast.error('Không tải được danh sách môn học', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return courses.filter((c) => {
      if (status !== 'ALL' && c.status !== status) return false;
      if (!keyword) return true;
      return [c.courseCode, c.courseName, c.description]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [courses, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const totalCredits = courses.reduce((sum, c) => sum + Number(c.credits || 0), 0);

  const openCreate = () => { setEditing(null); setForm(EMPTY); setErrors({}); setOpen(true); };

  const openEdit = (course) => {
    setEditing(course);
    setForm({
      courseCode: course.courseCode || '',
      courseName: course.courseName || '',
      credits: course.credits ?? 3,
      description: course.description || '',
      status: course.status || 'ACTIVE',
    });
    setErrors({});
    setOpen(true);
  };

  const validate = () => {
    const next = {};
    if (!editing && !form.courseCode.trim()) next.courseCode = 'Mã môn học là bắt buộc.';
    if (!form.courseName.trim()) next.courseName = 'Tên môn học là bắt buộc.';
    const credits = Number(form.credits);
    if (!Number.isInteger(credits) || credits < 1 || credits > 6) next.credits = 'Số tín chỉ phải từ 1 đến 6.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        courseCode: form.courseCode.trim(),
        courseName: form.courseName.trim(),
        credits: Number(form.credits),
        description: form.description.trim(),
        status: form.status,
      };
      if (editing) {
        await courseApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật môn học ${payload.courseName}.`);
      } else {
        await courseApi.create(payload);
        toast.success('Thêm môn học thành công', `${payload.courseName} đã được thêm vào hệ thống.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu môn học', getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý môn học"
        subtitle="Quản lý danh mục môn học, số tín chỉ và trạng thái mở."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm môn học</button>}
      />

      <div className="stats-grid">
        <StatCard label="Tổng môn học" value={courses.length} icon={<BookIcon />} tone="blue" />
        <StatCard label="Đang hoạt động" value={courses.filter((c) => c.status === 'ACTIVE').length} icon={<LayersIcon />} tone="green" />
        <StatCard label="Tổng tín chỉ" value={totalCredits} icon={<AwardIcon />} tone="amber" />
        <StatCard label="Tín chỉ trung bình" value={courses.length ? (totalCredits / courses.length).toFixed(1) : '—'} icon={<AwardIcon />} tone="indigo" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo mã hoặc tên môn học..." />
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Ngừng hoạt động</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} môn học</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<BookIcon />}
            title="Chưa có môn học"
            description="Thêm môn học mới hoặc điều chỉnh bộ lọc để xem dữ liệu."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm môn học</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã môn</th>
                    <th>Tên môn học</th>
                    <th>Tín chỉ</th>
                    <th>Mô tả</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((c) => (
                    <tr key={c.id}>
                      <td className="cell-strong">{c.courseCode}</td>
                      <td>{c.courseName}</td>
                      <td><span className="badge badge-blue">{c.credits} tín chỉ</span></td>
                      <td className="cell-muted" style={{ maxWidth: 320 }}>{c.description || '—'}</td>
                      <td><StatusBadge status={c.status} /></td>
                      <td>
                        <div className="row-actions">
                          <button className="btn btn-secondary btn-sm" onClick={() => openEdit(c)}><PencilIcon size={14} /> Sửa</button>
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
        title={editing ? 'Cập nhật môn học' : 'Thêm môn học mới'}
        subtitle={editing ? `Mã môn: ${editing.courseCode}` : 'Tạo môn học mới trong danh mục đào tạo.'}
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu môn học'}</button>
          </>
        )}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            <Field label="Mã môn học" required error={errors.courseCode}>
              <input className={`input${errors.courseCode ? ' invalid' : ''}`} value={form.courseCode} onChange={(e) => setForm({ ...form, courseCode: e.target.value })} disabled={!!editing} placeholder="VD: INT2204" />
            </Field>
            <Field label="Số tín chỉ" required error={errors.credits}>
              <input className={`input${errors.credits ? ' invalid' : ''}`} type="number" min="1" max="6" value={form.credits} onChange={(e) => setForm({ ...form, credits: e.target.value })} />
            </Field>
            <Field label="Tên môn học" required error={errors.courseName} className="full">
              <input className={`input${errors.courseName ? ' invalid' : ''}`} value={form.courseName} onChange={(e) => setForm({ ...form, courseName: e.target.value })} placeholder="VD: Lập trình hướng đối tượng" />
            </Field>
            <Field label="Mô tả" className="full">
              <textarea className="textarea" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Mô tả ngắn về môn học..." />
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

export default AdminCourses;
