import React, { useEffect, useMemo, useState } from 'react';
import { courseApi, courseSectionApi, lecturerApi, semesterApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  BookIcon, CalendarIcon, ClockIcon, LayersIcon, PencilIcon, PlusIcon, UserIcon, UsersIcon,
} from '../../components/Icons';
import {
  EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;
const EMPTY = {
  sectionCode: '', courseId: '', semesterId: '', lecturerId: '',
  capacity: 40, schedule: '', status: 'DRAFT',
};

const AdminSections = () => {
  const toast = useToast();
  const [sections, setSections] = useState([]);
  const [courses, setCourses] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [lecturers, setLecturers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [semesterFilter, setSemesterFilter] = useState('ALL');
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const [sectionRes, courseRes, semesterRes, lecturerRes] = await Promise.all([
        courseSectionApi.getAll(),
        courseApi.getAll(),
        semesterApi.getAll(),
        lecturerApi.getAll().catch(() => ({ data: [] })),
      ]);
      setSections(sectionRes.data || []);
      setCourses(courseRes.data || []);
      setSemesters(semesterRes.data || []);
      setLecturers(lecturerRes.data || []);
    } catch {
      toast.error('Không tải được dữ liệu lớp học phần', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return sections.filter((s) => {
      if (status !== 'ALL' && s.status !== status) return false;
      if (semesterFilter !== 'ALL' && String(s.semester?.id) !== semesterFilter) return false;
      if (!keyword) return true;
      return [s.sectionCode, s.course?.courseName, s.course?.courseCode, s.semester?.semesterName, s.lecturer?.fullName, s.schedule]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [sections, search, status, semesterFilter]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const totalCapacity = sections.reduce((sum, s) => sum + Number(s.capacity || 0), 0);

  const openCreate = () => { setEditing(null); setForm(EMPTY); setErrors({}); setOpen(true); };

  const openEdit = (section) => {
    setEditing(section);
    setForm({
      sectionCode: section.sectionCode || '',
      courseId: section.course?.id ? String(section.course.id) : '',
      semesterId: section.semester?.id ? String(section.semester.id) : '',
      lecturerId: section.lecturer?.id ? String(section.lecturer.id) : '',
      capacity: section.capacity ?? 40,
      schedule: section.schedule || '',
      status: section.status || 'DRAFT',
    });
    setErrors({});
    setOpen(true);
  };

  const validate = () => {
    const next = {};
    if (!editing && !form.sectionCode.trim()) next.sectionCode = 'Mã lớp là bắt buộc.';
    if (!editing && !form.courseId) next.courseId = 'Vui lòng chọn môn học.';
    if (!editing && !form.semesterId) next.semesterId = 'Vui lòng chọn học kỳ.';
    const capacity = Number(form.capacity);
    if (!Number.isInteger(capacity) || capacity < 1) next.capacity = 'Sĩ số phải là số nguyên dương.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        sectionCode: form.sectionCode.trim(),
        course: { id: Number(form.courseId) },
        semester: { id: Number(form.semesterId) },
        lecturer: form.lecturerId ? { id: Number(form.lecturerId) } : null,
        capacity: Number(form.capacity),
        schedule: form.schedule.trim(),
        status: form.status,
      };
      if (editing) {
        await courseSectionApi.update(editing.id, payload);
        toast.success('Cập nhật thành công', `Đã cập nhật lớp ${form.sectionCode}.`);
      } else {
        await courseSectionApi.create(payload);
        toast.success('Thêm lớp học phần thành công', `Lớp ${form.sectionCode} đã được tạo.`);
      }
      setOpen(false);
      load();
    } catch (err) {
      toast.error('Không thể lưu lớp học phần', getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý lớp học phần"
        subtitle="Tạo lớp học phần, phân công giảng viên và quản lý sĩ số theo học kỳ."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm lớp học phần</button>}
      />

      <div className="stats-grid">
        <StatCard label="Tổng lớp học phần" value={sections.length} icon={<LayersIcon />} tone="blue" />
        <StatCard label="Đang mở" value={sections.filter((s) => s.status === 'OPEN').length} icon={<BookIcon />} tone="green" />
        <StatCard label="Tổng sĩ số" value={totalCapacity} icon={<UsersIcon />} tone="amber" />
        <StatCard label="Chưa phân công" value={sections.filter((s) => !s.lecturer).length} icon={<UserIcon />} tone="red" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo mã lớp, môn học, giảng viên..." />
        <select className="select" style={{ width: 200 }} value={semesterFilter} onChange={(e) => { setSemesterFilter(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả học kỳ</option>
          {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName}</option>)}
        </select>
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="DRAFT">Bản nháp</option>
          <option value="OPEN">Đang mở</option>
          <option value="IN_PROGRESS">Đang diễn ra</option>
          <option value="CLOSED">Đã đóng</option>
          <option value="COMPLETED">Đã kết thúc</option>
          <option value="CANCELLED">Đã huỷ</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} lớp</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<LayersIcon />}
            title="Chưa có lớp học phần"
            description="Tạo lớp học phần mới hoặc điều chỉnh bộ lọc để xem dữ liệu."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm lớp học phần</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã lớp</th>
                    <th>Môn học</th>
                    <th>Học kỳ</th>
                    <th>Giảng viên</th>
                    <th>Lịch học</th>
                    <th>Sĩ số</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) => (
                    <tr key={s.id}>
                      <td className="cell-strong">{s.sectionCode}</td>
                      <td>
                        <div className="cell-strong">{s.course?.courseName || '—'}</div>
                        <div className="cell-muted">{s.course?.courseCode} · {s.course?.credits} tín chỉ</div>
                      </td>
                      <td className="cell-muted">{s.semester?.semesterName || '—'}</td>
                      <td>
                        {s.lecturer ? (
                          <div className="flex" style={{ gap: 6 }}><UserIcon size={14} style={{ color: 'var(--muted)' }} />{s.lecturer.fullName}</div>
                        ) : (
                          <span className="badge badge-amber">Chưa phân công</span>
                        )}
                      </td>
                      <td>
                        <div className="schedule-line"><ClockIcon size={14} />{s.schedule || '—'}</div>
                      </td>
                      <td>{s.capacity}</td>
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
        title={editing ? 'Cập nhật lớp học phần' : 'Thêm lớp học phần mới'}
        subtitle={editing ? `Mã lớp: ${editing.sectionCode}` : 'Tạo lớp học phần và phân công giảng viên.'}
        footer={(
          <>
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu lớp học phần'}</button>
          </>
        )}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            <Field label="Mã lớp học phần" required error={errors.sectionCode}>
              <input className={`input${errors.sectionCode ? ' invalid' : ''}`} value={form.sectionCode} onChange={(e) => setForm({ ...form, sectionCode: e.target.value })} disabled={!!editing} placeholder="VD: INT2204-01" />
            </Field>
            <Field label="Sĩ số tối đa" required error={errors.capacity}>
              <input className={`input${errors.capacity ? ' invalid' : ''}`} type="number" min="1" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
            </Field>
            <Field label="Môn học" required error={errors.courseId}>
              <select className={`select${errors.courseId ? ' invalid' : ''}`} value={form.courseId} onChange={(e) => setForm({ ...form, courseId: e.target.value })} disabled={!!editing}>
                <option value="">-- Chọn môn học --</option>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.courseCode} · {c.courseName}</option>)}
              </select>
            </Field>
            <Field label="Học kỳ" required error={errors.semesterId}>
              <select className={`select${errors.semesterId ? ' invalid' : ''}`} value={form.semesterId} onChange={(e) => setForm({ ...form, semesterId: e.target.value })} disabled={!!editing}>
                <option value="">-- Chọn học kỳ --</option>
                {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName}</option>)}
              </select>
            </Field>
            <Field label="Giảng viên">
              <select className="select" value={form.lecturerId} onChange={(e) => setForm({ ...form, lecturerId: e.target.value })}>
                <option value="">-- Chưa phân công --</option>
                {lecturers.map((l) => <option key={l.id} value={l.id}>{l.lecturerCode} · {l.fullName}</option>)}
              </select>
            </Field>
            <Field label="Trạng thái">
              <select className="select" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="DRAFT">Bản nháp</option>
                <option value="OPEN">Đang mở</option>
                <option value="IN_PROGRESS">Đang diễn ra</option>
                <option value="CLOSED">Đã đóng</option>
                <option value="COMPLETED">Đã kết thúc</option>
                <option value="CANCELLED">Đã huỷ</option>
              </select>
            </Field>
            <Field label="Lịch học" className="full" hint="Ví dụ: Thứ 2, 07:30 – 09:30, Phòng A2-301">
              <input className="input" value={form.schedule} onChange={(e) => setForm({ ...form, schedule: e.target.value })} placeholder="Thứ 2, 07:30 – 09:30, Phòng A2-301" />
            </Field>
          </div>
        </form>
      </Modal>
    </>
  );
};

export default AdminSections;
