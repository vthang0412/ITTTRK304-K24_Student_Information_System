import React, { useEffect, useMemo, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import {
  courseApi, courseSectionApi, examRoomApi, examScheduleApi, semesterApi,
} from '../../api';
import { Alert, EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge } from '../../components/UI';
import { CalendarIcon, ClockIcon, LayersIcon, PlusIcon, UsersIcon } from '../../components/Icons';
import { useToast } from '../../context/ToastContext';
import { downloadBlob, getErrorMessage } from '../../utils/format';
import './AdminExamSchedules.css';

const EMPTY_FORM = {
  semesterId: '', courseId: '', courseSectionId: '', examDate: '', startTime: '', endTime: '',
  examType: 'OFFLINE', roomId: '', examLocation: '', examLink: '', notes: '',
};
const todayInVietnam = () => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
};
const timeText = (value) => (value ? value.slice(0, 5) : '--:--');
const formatExamDate = (value) => {
  if (!value) return '—';
  const [year, month, day] = value.split('-').map(Number);
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(year, month - 1, day));
};

const AdminExamSchedules = () => {
  const toast = useToast();
  const [schedules, setSchedules] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [courses, setCourses] = useState([]);
  const [sections, setSections] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [stats, setStats] = useState({ total: 0, upcoming: 0, completed: 0, cancelled: 0 });
  const [filters, setFilters] = useState({
    keyword: '', semesterId: '', courseId: '', examType: '', status: '', fromDate: '', toDate: '',
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  const [loadedRequestKey, setLoadedRequestKey] = useState('');
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [eligibleCount, setEligibleCount] = useState(null);
  const [loadedCountSection, setLoadedCountSection] = useState('');
  const [detail, setDetail] = useState(null);
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);
  const requestKey = JSON.stringify([filters, page, pageSize, reloadKey]);
  const loading = requestKey !== loadedRequestKey;

  useEffect(() => {
    let active = true;
    Promise.all([
      semesterApi.getAll(),
      courseApi.getAll(),
      courseSectionApi.getAll(),
      examRoomApi.getAll({ page: 0, size: 1000 }),
    ]).then(([semesterResponse, courseResponse, sectionResponse, roomResponse]) => {
      if (!active) return;
      setSemesters(Array.isArray(semesterResponse.data) ? semesterResponse.data : []);
      setCourses(Array.isArray(courseResponse.data) ? courseResponse.data : []);
      setSections(Array.isArray(sectionResponse.data) ? sectionResponse.data : []);
      setRooms(Array.isArray(roomResponse.data?.content) ? roomResponse.data.content : []);
    }).catch((error) => {
      if (active) toast.error('Không tải được dữ liệu tạo lịch thi', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [toast]);

  useEffect(() => {
    let active = true;
    const params = {
      keyword: filters.keyword.trim() || undefined,
      semesterId: filters.semesterId || undefined,
      courseId: filters.courseId || undefined,
      examType: filters.examType || undefined,
      status: filters.status || undefined,
      fromDate: filters.fromDate || undefined,
      toDate: filters.toDate || undefined,
      page: page - 1,
      size: pageSize,
      sort: 'examDate,asc',
    };
    examScheduleApi.getAdmin(params).then(({ data }) => {
      if (!active) return;
      setSchedules(Array.isArray(data?.content) ? data.content : []);
      setTotalItems(Number(data?.totalElements || 0));
      setLoadError('');
      setLoadedRequestKey(requestKey);
    }).catch((error) => {
      if (!active) return;
      setSchedules([]);
      setTotalItems(0);
      setLoadError(getErrorMessage(error));
      setLoadedRequestKey(requestKey);
      toast.error('Không tải được danh sách lịch thi', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [filters, page, pageSize, reloadKey, requestKey, toast]);

  useEffect(() => {
    let active = true;
    const requests = [
      examScheduleApi.getAdmin({ page: 0, size: 1 }),
      examScheduleApi.getAdmin({ page: 0, size: 1, status: 'SCHEDULED', fromDate: todayInVietnam() }),
      examScheduleApi.getAdmin({ page: 0, size: 1, status: 'COMPLETED' }),
      examScheduleApi.getAdmin({ page: 0, size: 1, status: 'CANCELLED' }),
    ];
    Promise.all(requests).then((responses) => {
      if (!active) return;
      setStats({
        total: Number(responses[0].data?.totalElements || 0),
        upcoming: Number(responses[1].data?.totalElements || 0),
        completed: Number(responses[2].data?.totalElements || 0),
        cancelled: Number(responses[3].data?.totalElements || 0),
      });
    }).catch((error) => {
      if (active) toast.error('Không tải được thống kê lịch thi', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [reloadKey, toast]);

  useEffect(() => {
    if (!form.courseSectionId) {
      return undefined;
    }
    let active = true;
    examScheduleApi.getEligibleStudentCount(form.courseSectionId)
      .then(({ data }) => {
        if (!active) return;
        setEligibleCount(Number(data?.count || 0));
        setLoadedCountSection(String(form.courseSectionId));
      })
      .catch((error) => {
        if (!active) return;
        setEligibleCount(null);
        setLoadedCountSection(String(form.courseSectionId));
        toast.error('Không tải được số sinh viên đủ điều kiện', getErrorMessage(error));
      });
    return () => { active = false; };
  }, [form.courseSectionId, toast]);

  const loadingCount = Boolean(form.courseSectionId) && loadedCountSection !== String(form.courseSectionId);
  const currentEligibleCount = loadingCount || !form.courseSectionId ? null : eligibleCount;
  const formSections = useMemo(() => sections.filter((section) => (
    (!form.semesterId || String(section.semester?.id) === String(form.semesterId))
    && (!form.courseId || String(section.course?.id) === String(form.courseId))
  )), [sections, form.semesterId, form.courseId]);
  const formCourses = useMemo(() => (
    form.semesterId
      ? courses.filter((course) => sections.some((section) => (
        String(section.semester?.id) === String(form.semesterId)
        && String(section.course?.id) === String(course.id)
      )))
      : courses
  ), [courses, sections, form.semesterId]);
  const selectedRoom = rooms.find((room) => String(room.id) === String(form.roomId));
  const roomOverCapacity = form.examType === 'OFFLINE'
    && selectedRoom
    && currentEligibleCount !== null
    && currentEligibleCount > Number(selectedRoom.capacity);

  const updateFilter = (key, value) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(1);
  };
  const refresh = () => setReloadKey((value) => value + 1);
  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setEligibleCount(null);
    setLoadedCountSection('');
    setFormErrors({});
    setServerError('');
    setFormOpen(true);
  };
  const openEdit = (schedule) => {
    const section = sections.find((item) => item.id === schedule.courseSectionId);
    setEditing(schedule);
    setEligibleCount(null);
    setLoadedCountSection('');
    setForm({
      semesterId: String(schedule.semesterId || section?.semester?.id || ''),
      courseId: String(section?.course?.id || ''),
      courseSectionId: String(schedule.courseSectionId || ''),
      examDate: schedule.examDate || '',
      startTime: timeText(schedule.startTime),
      endTime: timeText(schedule.endTime),
      examType: schedule.examType || 'OFFLINE',
      roomId: schedule.roomId ? String(schedule.roomId) : '',
      examLocation: schedule.examLocation || '',
      examLink: schedule.examLink || '',
      notes: schedule.notes || '',
    });
    setFormErrors({});
    setServerError('');
    setFormOpen(true);
  };
  const validate = () => {
    const errors = {};
    if (!form.semesterId) errors.semesterId = 'Vui lòng chọn học kỳ.';
    if (!form.courseId) errors.courseId = 'Vui lòng chọn môn học.';
    if (!form.courseSectionId) errors.courseSectionId = 'Vui lòng chọn lớp học phần.';
    if (!form.examDate) errors.examDate = 'Vui lòng chọn ngày thi.';
    if (!form.startTime) errors.startTime = 'Vui lòng chọn giờ bắt đầu.';
    if (!form.endTime || form.endTime <= form.startTime) errors.endTime = 'Giờ kết thúc phải sau giờ bắt đầu.';
    if (!form.examType) errors.examType = 'Vui lòng chọn hình thức thi.';
    if (form.examType === 'OFFLINE' && !form.roomId) errors.roomId = 'Thi trực tiếp bắt buộc chọn phòng thi.';
    if (roomOverCapacity) errors.roomId = 'Số sinh viên dự thi vượt sức chứa phòng.';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };
  const saveSchedule = async (event) => {
    event.preventDefault();
    if (savingRef.current || !validate()) return;
    savingRef.current = true;
    setSaving(true);
    setServerError('');
    const payload = {
      courseSectionId: Number(form.courseSectionId),
      examDate: form.examDate,
      startTime: `${form.startTime}:00`,
      endTime: `${form.endTime}:00`,
      examType: form.examType,
      roomId: form.roomId ? Number(form.roomId) : null,
      examLocation: form.examLocation.trim() || null,
      examLink: form.examLink.trim() || null,
      notes: form.notes.trim() || null,
    };
    try {
      if (editing) {
        await examScheduleApi.update(editing.id, payload);
        toast.success('Cập nhật lịch thi thành công');
      } else {
        await examScheduleApi.create(payload);
        toast.success('Tạo lịch thi thành công');
      }
      setFormOpen(false);
      refresh();
    } catch (error) {
      const message = getErrorMessage(error);
      setServerError(message);
      toast.error('Không thể lưu lịch thi', message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };
  const confirmAction = async (schedule, action) => {
    const canceling = action === 'cancel';
    const result = await Swal.fire({
      title: canceling ? 'Hủy lịch thi?' : 'Xóa lịch thi?',
      text: canceling
        ? `Lịch thi ${schedule.courseName} - ${schedule.sectionCode} sẽ được chuyển sang trạng thái đã hủy.`
        : `Lịch thi ${schedule.courseName} - ${schedule.sectionCode} sẽ bị xóa vĩnh viễn.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: canceling ? 'Hủy lịch thi' : 'Xóa lịch thi',
      cancelButtonText: 'Quay lại',
      reverseButtons: true,
    });
    if (!result.isConfirmed) return;
    try {
      if (canceling) await examScheduleApi.cancel(schedule.id);
      else await examScheduleApi.delete(schedule.id);
      toast.success(canceling ? 'Đã hủy lịch thi' : 'Đã xóa lịch thi');
      refresh();
    } catch (error) {
      toast.error(canceling ? 'Không thể hủy lịch thi' : 'Không thể xóa lịch thi', getErrorMessage(error));
    }
  };
  const showRoster = async (schedule) => {
    setRoster({ schedule, students: [] });
    setRosterLoading(true);
    try {
      const { data } = await examScheduleApi.getStudents(schedule.id);
      setRoster({ schedule, students: Array.isArray(data) ? data : [] });
    } catch (error) {
      setRoster(null);
      toast.error('Không tải được danh sách sinh viên dự thi', getErrorMessage(error));
    } finally {
      setRosterLoading(false);
    }
  };
  const exportSchedules = async () => {
    try {
      const params = {
        keyword: filters.keyword.trim() || undefined,
        semesterId: filters.semesterId || undefined,
        courseId: filters.courseId || undefined,
        examType: filters.examType || undefined,
        status: filters.status || undefined,
        fromDate: filters.fromDate || undefined,
        toDate: filters.toDate || undefined,
      };
      const response = await examScheduleApi.exportAdmin(params);
      const semester = semesters.find((item) => String(item.id) === String(filters.semesterId));
      const filename = semester?.semesterName
        ? `Exam_Schedules_${semester.semesterName.replace(/[^a-z0-9_-]+/gi, '_')}.xlsx`
        : 'Exam_Schedules.xlsx';
      downloadBlob(response.data, filename);
    } catch (error) {
      toast.error('Không thể xuất Excel lịch thi', getErrorMessage(error));
    }
  };

  return (
    <div className="exam-print-area">
      <PageHeader
        title="Quản lý lịch thi"
        subtitle="Lập lịch thi, kiểm tra phòng và xung đột sinh viên từ dữ liệu hệ thống."
        actions={(
          <div className="no-print page-head-actions">
            <button className="btn btn-secondary" onClick={() => window.print()}>In lịch thi</button>
            <button className="btn btn-secondary" onClick={exportSchedules}>Xuất Excel</button>
            <button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm lịch thi</button>
          </div>
        )}
      />

      <div className="exam-print-title">
        <h1>Lịch thi</h1>
        <p>Ngày in: {formatExamDate(todayInVietnam())}</p>
      </div>

      <div className="stats-grid">
        <StatCard label="Tổng số lịch thi" value={stats.total} icon={<CalendarIcon />} tone="blue" />
        <StatCard label="Lịch thi sắp tới" value={stats.upcoming} icon={<ClockIcon />} tone="amber" />
        <StatCard label="Đã hoàn thành" value={stats.completed} icon={<LayersIcon />} tone="green" />
        <StatCard label="Đã hủy" value={stats.cancelled} icon={<UsersIcon />} tone="red" />
      </div>

      <div className="toolbar no-print admin-exam-schedule-filters">
        <div className="exam-schedule-filter-row exam-schedule-filter-row-main">
          <div className="exam-schedule-filter-field exam-schedule-filter-search">
            <SearchInput value={filters.keyword} onChange={(value) => updateFilter('keyword', value)} placeholder="Tìm môn học, lớp hoặc phòng..." />
          </div>
          <div className="exam-schedule-filter-field exam-schedule-filter-semester">
            <select className="select" value={filters.semesterId} onChange={(event) => updateFilter('semesterId', event.target.value)}>
              <option value="">Tất cả học kỳ</option>
              {semesters.map((semester) => <option key={semester.id} value={semester.id}>{semester.semesterName}</option>)}
            </select>
          </div>
          <div className="exam-schedule-filter-field exam-schedule-filter-course">
            <select className="select" value={filters.courseId} onChange={(event) => updateFilter('courseId', event.target.value)}>
              <option value="">Tất cả môn học</option>
              {courses.map((course) => <option key={course.id} value={course.id}>{course.courseCode} - {course.courseName}</option>)}
            </select>
          </div>
          <div className="exam-schedule-filter-field exam-schedule-filter-type">
            <select className="select" value={filters.examType} onChange={(event) => updateFilter('examType', event.target.value)}>
              <option value="">Mọi hình thức</option>
              <option value="OFFLINE">Offline</option>
              <option value="ONLINE">Online</option>
            </select>
          </div>
        </div>
        <div className="exam-schedule-filter-row exam-schedule-filter-row-dates">
          <div className="exam-schedule-filter-field exam-schedule-filter-from-date">
            <input aria-label="Từ ngày thi" className="input exam-filter-date" type="date" value={filters.fromDate} onChange={(event) => updateFilter('fromDate', event.target.value)} />
          </div>
          <div className="exam-schedule-filter-field exam-schedule-filter-to-date">
            <input aria-label="Đến ngày thi" className="input exam-filter-date" type="date" value={filters.toDate} onChange={(event) => updateFilter('toDate', event.target.value)} />
          </div>
          <div className="exam-schedule-filter-field exam-schedule-filter-status">
            <select className="select" value={filters.status} onChange={(event) => updateFilter('status', event.target.value)}>
              <option value="">Tất cả trạng thái</option>
              <option value="SCHEDULED">Đã lên lịch</option>
              <option value="COMPLETED">Đã hoàn thành</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </div>
        </div>
      </div>

      <section className="card">
        {loading ? <LoadingBlock /> : loadError ? (
          <Alert tone="error">
            {loadError}
            <button className="btn btn-sm btn-secondary" style={{ marginLeft: 12 }} onClick={refresh}>Thử lại</button>
          </Alert>
        ) : schedules.length === 0 ? (
          <EmptyState
            icon={<CalendarIcon />}
            title="Chưa có lịch thi phù hợp"
            description="Tạo lịch thi mới hoặc điều chỉnh các bộ lọc."
            action={<button className="btn btn-primary no-print" onClick={openCreate}><PlusIcon /> Thêm lịch thi</button>}
          />
        ) : (
          <>
            <div className="table-wrap admin-exam-schedule-table-wrap">
              <table className="table admin-exam-schedule-table">
                <colgroup>
                  <col className="exam-col-number" />
                  <col className="exam-col-course" />
                  <col className="exam-col-section" />
                  <col className="exam-col-semester" />
                  <col className="exam-col-date" />
                  <col className="exam-col-time" />
                  <col className="exam-col-room" />
                  <col className="exam-col-type" />
                  <col className="exam-col-status" />
                  <col className="exam-col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th className="exam-header-number">STT</th>
                    <th className="exam-header-course">Môn học</th>
                    <th className="exam-header-section">Lớp học phần</th>
                    <th className="exam-header-semester">Học kỳ</th>
                    <th className="exam-header-date">Ngày thi</th>
                    <th className="exam-header-time">Thời gian</th>
                    <th className="exam-header-room">Phòng thi</th>
                    <th className="exam-header-type">Hình thức</th>
                    <th className="exam-header-status">Trạng thái</th>
                    <th className="exam-header-actions no-print">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {schedules.map((schedule, index) => (
                    <tr key={schedule.id}>
                      <td className="exam-cell-number">{(page - 1) * pageSize + index + 1}</td>
                      <td className="exam-cell-course"><strong>{schedule.courseName || '—'}</strong><div className="muted small">{schedule.courseCode || '—'}</div></td>
                      <td className="exam-cell-section">{schedule.sectionCode || '—'}</td>
                      <td className="exam-cell-semester">{schedule.semesterName || '—'}</td>
                      <td className="exam-cell-date">{formatExamDate(schedule.examDate)}</td>
                      <td className="exam-cell-time">{timeText(schedule.startTime)} – {timeText(schedule.endTime)}</td>
                      <td className="exam-cell-room">{schedule.roomCode || schedule.examLocation || (schedule.examType === 'ONLINE' ? 'Online' : '—')}</td>
                      <td className="exam-cell-type">{schedule.examType === 'ONLINE' ? 'Online' : 'Offline'}</td>
                      <td className="exam-cell-status"><StatusBadge status={schedule.status} /></td>
                      <td className="exam-cell-actions no-print">
                        <div className="exam-row-actions">
                          <button className="btn btn-sm btn-secondary exam-action-slot" onClick={() => setDetail(schedule)}>Xem</button>
                          {schedule.status !== 'COMPLETED'
                            ? <button className="btn btn-sm btn-secondary exam-action-slot" onClick={() => openEdit(schedule)}>Sửa</button>
                            : <span className="exam-action-placeholder exam-action-slot" aria-hidden="true" />}
                          {schedule.status === 'SCHEDULED'
                            ? <button className="btn btn-sm btn-warning exam-action-slot" onClick={() => confirmAction(schedule, 'cancel')}>Hủy</button>
                            : <span className="exam-action-placeholder exam-action-slot" aria-hidden="true" />}
                          <button className="btn btn-sm btn-danger exam-action-slot" onClick={() => confirmAction(schedule, 'delete')}>Xóa</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(value) => { setPageSize(value); setPage(1); }}
            />
          </>
        )}
      </section>

      <Modal
        open={formOpen}
        onClose={() => { if (!saving) setFormOpen(false); }}
        title={editing ? 'Chỉnh sửa lịch thi' : 'Thêm lịch thi'}
        subtitle="Thông tin học kỳ, môn học và sĩ số được lấy từ dữ liệu hệ thống."
        size="lg"
        footer={(
          <>
            <button className="btn btn-secondary" type="button" onClick={() => setFormOpen(false)} disabled={saving}>Đóng</button>
            <button className="btn btn-primary" type="submit" form="exam-schedule-form" disabled={saving}>
              {saving ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Tạo lịch thi'}
            </button>
          </>
        )}
      >
        <form id="exam-schedule-form" onSubmit={saveSchedule}>
          {serverError && <Alert tone="error">{serverError}</Alert>}
          <div className="form-grid">
            <Field label="Học kỳ" required error={formErrors.semesterId}>
              <select className="select" value={form.semesterId} onChange={(event) => setForm((value) => ({
                ...value, semesterId: event.target.value, courseId: '', courseSectionId: '',
              }))}>
                <option value="">Chọn học kỳ</option>
                {semesters.map((semester) => <option key={semester.id} value={semester.id}>{semester.semesterName}</option>)}
              </select>
            </Field>
            <Field label="Môn học" required error={formErrors.courseId}>
              <select className="select" value={form.courseId} disabled={!form.semesterId} onChange={(event) => setForm((value) => ({
                ...value, courseId: event.target.value, courseSectionId: '',
              }))}>
                <option value="">{form.semesterId ? 'Chọn môn học' : 'Chọn học kỳ trước'}</option>
                {formCourses.map((course) => <option key={course.id} value={course.id}>{course.courseCode} - {course.courseName}</option>)}
              </select>
            </Field>
            <Field label="Lớp học phần" required error={formErrors.courseSectionId}>
              <select className="select" value={form.courseSectionId} disabled={!form.courseId} onChange={(event) => setForm((value) => ({ ...value, courseSectionId: event.target.value }))}>
                <option value="">{form.courseId ? 'Chọn lớp học phần' : 'Chọn môn học trước'}</option>
                {formSections.map((section) => <option key={section.id} value={section.id}>{section.sectionCode}</option>)}
              </select>
            </Field>
            <Field label="Số sinh viên đủ điều kiện">
              <div className="input exam-readonly-field">{loadingCount ? 'Đang tải...' : currentEligibleCount === null ? '—' : `${currentEligibleCount} sinh viên đã đăng ký`}</div>
            </Field>
            <Field label="Ngày thi" required error={formErrors.examDate}>
              <input className="input" type="date" value={form.examDate} onChange={(event) => setForm((value) => ({ ...value, examDate: event.target.value }))} />
            </Field>
            <Field label="Hình thức thi" required error={formErrors.examType}>
              <select className="select" value={form.examType} onChange={(event) => setForm((value) => ({
                ...value, examType: event.target.value, roomId: event.target.value === 'ONLINE' ? '' : value.roomId,
              }))}>
                <option value="OFFLINE">Offline</option>
                <option value="ONLINE">Online</option>
              </select>
            </Field>
            <Field label="Giờ bắt đầu" required error={formErrors.startTime}>
              <input className="input" type="time" value={form.startTime} onChange={(event) => setForm((value) => ({ ...value, startTime: event.target.value }))} />
            </Field>
            <Field label="Giờ kết thúc" required error={formErrors.endTime}>
              <input className="input" type="time" value={form.endTime} onChange={(event) => setForm((value) => ({ ...value, endTime: event.target.value }))} />
            </Field>
            <Field label="Phòng thi" required={form.examType === 'OFFLINE'} error={formErrors.roomId}
              hint={form.examType === 'ONLINE' ? 'Phòng thi không bắt buộc với hình thức online.' : ''}>
              <select className="select" value={form.roomId} disabled={form.examType === 'ONLINE'} onChange={(event) => setForm((value) => ({ ...value, roomId: event.target.value }))}>
                <option value="">Chọn phòng thi</option>
                {rooms.filter((room) => room.status === 'ACTIVE' || String(room.id) === String(form.roomId))
                  .map((room) => <option key={room.id} value={room.id}>{room.roomCode} - {room.roomName} ({room.capacity} chỗ)</option>)}
              </select>
            </Field>
            <Field label="Sức chứa phòng">
              <div className="input exam-readonly-field">{selectedRoom ? `${selectedRoom.capacity} chỗ` : '—'}</div>
            </Field>
            {roomOverCapacity && (
              <div className="full"><Alert tone="error">Số sinh viên dự thi ({currentEligibleCount}) vượt sức chứa phòng ({selectedRoom.capacity}).</Alert></div>
            )}
            <Field label="Địa điểm thi">
              <input className="input" value={form.examLocation} onChange={(event) => setForm((value) => ({ ...value, examLocation: event.target.value }))} placeholder="Ví dụ: Phòng A101 hoặc nền tảng thi" />
            </Field>
            <Field label="Link thi online">
              <input className="input" type="url" value={form.examLink} onChange={(event) => setForm((value) => ({ ...value, examLink: event.target.value }))} placeholder="https://" />
            </Field>
            <Field label="Ghi chú" className="full">
              <textarea className="input" rows="3" value={form.notes} onChange={(event) => setForm((value) => ({ ...value, notes: event.target.value }))} />
            </Field>
          </div>
        </form>
      </Modal>

      <Modal open={Boolean(detail)} onClose={() => setDetail(null)} title="Chi tiết lịch thi" size="md">
        {detail && (
          <div className="info-list">
            <div className="info-row"><span>Môn học</span><strong>{detail.courseCode} - {detail.courseName}</strong></div>
            <div className="info-row"><span>Lớp học phần</span><strong>{detail.sectionCode}</strong></div>
            <div className="info-row"><span>Học kỳ</span><strong>{detail.semesterName}</strong></div>
            <div className="info-row"><span>Ngày thi</span><strong>{formatExamDate(detail.examDate)}</strong></div>
            <div className="info-row"><span>Thời gian</span><strong>{timeText(detail.startTime)} – {timeText(detail.endTime)}</strong></div>
            <div className="info-row"><span>Phòng / địa điểm</span><strong>{detail.roomCode || detail.examLocation || 'Online'}</strong></div>
            <div className="info-row"><span>Hình thức</span><strong>{detail.examType}</strong></div>
            <div className="info-row"><span>Sinh viên dự thi</span><strong>{detail.enrolledStudentsCount}</strong></div>
            <div className="info-row"><span>Trạng thái</span><StatusBadge status={detail.status} /></div>
            {detail.examLink && <div className="info-row"><span>Link thi</span><a href={detail.examLink} target="_blank" rel="noreferrer">Mở link thi</a></div>}
            {detail.notes && <div className="info-row"><span>Ghi chú</span><strong>{detail.notes}</strong></div>}
            <button className="btn btn-secondary no-print" onClick={() => showRoster(detail)}>Danh sách sinh viên dự thi</button>
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(roster)}
        onClose={() => { if (!rosterLoading) setRoster(null); }}
        title={`Sinh viên dự thi${roster?.schedule ? ` - ${roster.schedule.sectionCode}` : ''}`}
        size="lg"
        headerRight={roster && (
          <button className="btn btn-secondary btn-sm no-print" onClick={async () => {
            try {
              const { data } = await examScheduleApi.exportStudents(roster.schedule.id);
              downloadBlob(data, `Exam_Students_${roster.schedule.id}.xlsx`);
            } catch (error) {
              toast.error('Không thể xuất danh sách sinh viên', getErrorMessage(error));
            }
          }}>Xuất Excel</button>
        )}
      >
        {rosterLoading ? <LoadingBlock text="Đang tải danh sách..." /> : roster?.students.length ? (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>STT</th><th>Mã sinh viên</th><th>Họ và tên</th><th>Email</th><th>Lớp</th></tr></thead>
              <tbody>{roster.students.map((student, index) => (
                <tr key={student.studentId}><td>{index + 1}</td><td>{student.studentCode}</td><td>{student.fullName}</td><td>{student.email}</td><td>{student.className || '—'}</td></tr>
              ))}</tbody>
            </table>
          </div>
        ) : <EmptyState title="Chưa có sinh viên đủ điều kiện" />}
      </Modal>
    </div>
  );
};

export default AdminExamSchedules;
