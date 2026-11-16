import React, { useEffect, useMemo, useRef, useState } from 'react';
import { examScheduleApi, semesterApi } from '../../api';
import { CalendarIcon, ClockIcon, LayersIcon } from '../../components/Icons';
import { Alert, EmptyState, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge } from '../../components/UI';
import { useToast } from '../../context/ToastContext';
import { downloadBlob, getErrorMessage } from '../../utils/format';
import './StudentExamSchedules.css';

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

const StudentExamSchedules = () => {
  const toast = useToast();
  const [schedules, setSchedules] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [semesterId, setSemesterId] = useState('');
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [sortDirection, setSortDirection] = useState('asc');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loadedSemesterKey, setLoadedSemesterKey] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const detailRequestRef = useRef(0);
  const semesterKey = `${semesterId || 'all'}:${reloadKey}`;
  const loading = loadedSemesterKey !== semesterKey;

  useEffect(() => {
    let active = true;
    semesterApi.getAll().then(({ data }) => {
      if (active) setSemesters(Array.isArray(data) ? data : []);
    }).catch((error) => {
      if (active) toast.error('Không tải được danh sách học kỳ', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [toast]);

  useEffect(() => {
    let active = true;
    const params = { semesterId: semesterId || undefined };
    Promise.all([
      examScheduleApi.getMine(params),
      examScheduleApi.getMine({ ...params, status: 'CANCELLED' }),
    ]).then(([activeResponse, cancelledResponse]) => {
      if (!active) return;
      const byId = new Map();
      [...(activeResponse.data || []), ...(cancelledResponse.data || [])].forEach((schedule) => byId.set(schedule.id, schedule));
      setSchedules(Array.from(byId.values()));
      setLoadError('');
      setLoadedSemesterKey(semesterKey);
    }).catch((error) => {
      if (!active) return;
      setSchedules([]);
      setLoadError(getErrorMessage(error));
      setLoadedSemesterKey(semesterKey);
      toast.error('Không tải được lịch thi cá nhân', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [semesterId, semesterKey, toast]);

  const filtered = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase('vi');
    return schedules.filter((schedule) => {
      if (status && schedule.status !== status) return false;
      if (!normalized) return true;
      return [
        schedule.courseCode, schedule.courseName, schedule.sectionCode, schedule.semesterName,
        schedule.roomCode, schedule.examLocation,
      ].filter(Boolean).some((value) => value.toLocaleLowerCase('vi').includes(normalized));
    }).sort((left, right) => {
      const leftCancelled = left.status === 'CANCELLED';
      const rightCancelled = right.status === 'CANCELLED';
      if (leftCancelled !== rightCancelled) return leftCancelled ? 1 : -1;
      const leftUpcoming = left.examDate >= todayInVietnam() && left.status === 'SCHEDULED';
      const rightUpcoming = right.examDate >= todayInVietnam() && right.status === 'SCHEDULED';
      if (leftUpcoming !== rightUpcoming) return leftUpcoming ? -1 : 1;
      const dateOrder = (left.examDate || '').localeCompare(right.examDate || '');
      return sortDirection === 'asc' ? dateOrder : -dateOrder;
    });
  }, [schedules, status, keyword, sortDirection]);
  const pageItems = filtered.slice((page - 1) * pageSize, page * pageSize);
  const activeSchedules = schedules.filter((schedule) => schedule.status !== 'CANCELLED');
  const stats = {
    total: activeSchedules.length,
    upcoming: activeSchedules.filter((schedule) => schedule.status === 'SCHEDULED' && schedule.examDate >= todayInVietnam()).length,
    completed: activeSchedules.filter((schedule) => schedule.status === 'COMPLETED').length,
    cancelled: schedules.filter((schedule) => schedule.status === 'CANCELLED').length,
  };
  const openDetail = async (schedule) => {
    const requestId = ++detailRequestRef.current;
    setDetail(schedule);
    setDetailLoading(true);
    try {
      const { data } = await examScheduleApi.getMineById(schedule.id);
      if (requestId === detailRequestRef.current) setDetail(data);
    } catch (error) {
      if (requestId === detailRequestRef.current) toast.error('Không tải được chi tiết lịch thi', getErrorMessage(error));
    } finally {
      if (requestId === detailRequestRef.current) setDetailLoading(false);
    }
  };
  const exportSchedule = async () => {
    try {
      const { data } = await examScheduleApi.exportMine({
        semesterId: semesterId || undefined,
        status: status || undefined,
      });
      downloadBlob(data, 'My_Exam_Schedules.xlsx');
    } catch (error) {
      toast.error('Không thể xuất Excel lịch thi', getErrorMessage(error));
    }
  };
  const updateFilter = (callback) => {
    callback();
    setPage(1);
  };

  return (
    <div className="exam-print-area">
      <PageHeader
        title="Lịch thi cá nhân"
        subtitle="Tra cứu lịch thi của các lớp học phần bạn đã đăng ký."
        actions={(
          <div className="no-print page-head-actions">
            <button className="btn btn-secondary" onClick={() => window.print()}>In lịch thi</button>
            <button className="btn btn-primary" onClick={exportSchedule}>Xuất Excel</button>
          </div>
        )}
      />
      <div className="exam-print-title">
        <h1>Lịch thi cá nhân</h1>
        <p>Ngày in: {formatExamDate(todayInVietnam())}</p>
      </div>
      <div className="stats-grid">
        <StatCard label="Môn phải thi" value={stats.total} icon={<LayersIcon />} tone="blue" />
        <StatCard label="Kỳ thi sắp tới" value={stats.upcoming} icon={<ClockIcon />} tone="amber" />
        <StatCard label="Đã hoàn thành" value={stats.completed} icon={<CalendarIcon />} tone="green" />
        <StatCard label="Đã hủy" value={stats.cancelled} icon={<CalendarIcon />} tone="red" />
      </div>
      <div className="toolbar no-print student-exam-schedule-filters">
        <SearchInput value={keyword} onChange={(value) => updateFilter(() => setKeyword(value))} placeholder="Tìm môn học, mã môn, lớp..." />
        <select className="select" value={semesterId} onChange={(event) => updateFilter(() => setSemesterId(event.target.value))}>
          <option value="">Tất cả học kỳ</option>
          {semesters.map((semester) => <option key={semester.id} value={semester.id}>{semester.semesterName}</option>)}
        </select>
        <select className="select" value={status} onChange={(event) => updateFilter(() => setStatus(event.target.value))}>
          <option value="">Tất cả trạng thái</option>
          <option value="SCHEDULED">Đã lên lịch</option>
          <option value="COMPLETED">Đã hoàn thành</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
        <select className="select" value={sortDirection} onChange={(event) => updateFilter(() => setSortDirection(event.target.value))} aria-label="Sắp xếp theo ngày thi">
          <option value="asc">Ngày thi gần nhất</option>
          <option value="desc">Ngày thi xa nhất</option>
        </select>
      </div>
      <section className="card">
        {loading ? <LoadingBlock /> : loadError ? (
          <Alert tone="error">
            {loadError}
            <button className="btn btn-sm btn-secondary" style={{ marginLeft: 12 }} onClick={() => setReloadKey((value) => value + 1)}>Tải lại</button>
          </Alert>
        ) : pageItems.length === 0 ? (
          <EmptyState
            icon={<CalendarIcon />}
            title="Chưa có lịch thi phù hợp"
            description="Lịch thi cá nhân sẽ xuất hiện khi học phần đã đăng ký có lịch thi."
          />
        ) : (
          <>
            <div className="table-wrap student-exam-schedules-table-wrap">
              <table className="table student-exam-schedules-table">
                <colgroup>
                  <col className="student-col-course" />
                  <col className="student-col-code" />
                  <col className="student-col-section" />
                  <col className="student-col-semester" />
                  <col className="student-col-date" />
                  <col className="student-col-time" />
                  <col className="student-col-time" />
                  <col className="student-col-room" />
                  <col className="student-col-type" />
                  <col className="student-col-status" />
                  <col className="student-col-detail" />
                </colgroup>
                <thead>
                  <tr><th>Môn học</th><th>Mã môn</th><th>Lớp học phần</th><th>Học kỳ</th><th>Ngày thi</th><th>Bắt đầu</th><th>Kết thúc</th><th>Phòng / địa điểm</th><th>Hình thức</th><th>Trạng thái</th><th className="no-print">Chi tiết</th></tr>
                </thead>
                <tbody>
                  {pageItems.map((schedule) => (
                    <tr key={schedule.id}>
                      <td><strong>{schedule.courseName || '—'}</strong></td>
                      <td>{schedule.courseCode || '—'}</td>
                      <td>{schedule.sectionCode || '—'}</td>
                      <td>{schedule.semesterName || '—'}</td>
                      <td>{formatExamDate(schedule.examDate)}</td>
                      <td>{timeText(schedule.startTime)}</td>
                      <td>{timeText(schedule.endTime)}</td>
                      <td>{schedule.roomCode || schedule.examLocation || (schedule.examType === 'ONLINE' ? 'Online' : '—')}</td>
                      <td>{schedule.examType === 'ONLINE' ? 'Online' : 'Offline'}</td>
                      <td><StatusBadge status={schedule.status} /></td>
                      <td className="no-print">
                        <button className="btn btn-sm btn-secondary student-exam-detail-btn" onClick={() => openDetail(schedule)}>Xem</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={filtered.length}
              onPageChange={setPage}
              onPageSizeChange={(value) => { setPageSize(value); setPage(1); }}
            />
          </>
        )}
      </section>
      <Modal
        open={Boolean(detail)}
        onClose={() => {
          detailRequestRef.current += 1;
          setDetail(null);
          setDetailLoading(false);
        }}
        title="Chi tiết lịch thi"
        size="md"
      >
        {detail && (
          <div className="info-list">
            {detailLoading && <LoadingBlock text="Đang cập nhật chi tiết..." />}
            <div className="info-row"><span>Môn học</span><strong>{detail.courseCode} - {detail.courseName}</strong></div>
            <div className="info-row"><span>Lớp học phần</span><strong>{detail.sectionCode}</strong></div>
            <div className="info-row"><span>Học kỳ</span><strong>{detail.semesterName}</strong></div>
            <div className="info-row"><span>Ngày thi</span><strong>{formatExamDate(detail.examDate)}</strong></div>
            <div className="info-row"><span>Thời gian</span><strong>{timeText(detail.startTime)} – {timeText(detail.endTime)}</strong></div>
            <div className="info-row"><span>Phòng / địa điểm</span><strong>{detail.roomCode || detail.examLocation || 'Online'}</strong></div>
            <div className="info-row"><span>Hình thức</span><strong>{detail.examType}</strong></div>
            <div className="info-row"><span>Trạng thái</span><StatusBadge status={detail.status} /></div>
            {detail.examLink && <div className="info-row"><span>Link thi online</span><a href={detail.examLink} target="_blank" rel="noreferrer">Mở link thi</a></div>}
            {detail.notes && <div className="info-row"><span>Ghi chú</span><strong>{detail.notes}</strong></div>}
            {detail.status === 'CANCELLED' && <Alert tone="info">Lịch thi này đã được hủy. Hãy kiểm tra thông báo mới từ nhà trường.</Alert>}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default StudentExamSchedules;
