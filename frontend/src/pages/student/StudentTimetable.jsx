import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { semesterApi, timetableApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CalendarIcon, ClockIcon } from '../../components/Icons';
import { EmptyState, LoadingBlock, PageHeader, Pagination } from '../../components/UI';
import { getErrorMessage } from '../../utils/format';
import { PAGE_SIZE_OPTIONS } from '../../hooks/usePaginatedList';

const DAYS = [
  { index: 1, label: 'Thứ Hai' },
  { index: 2, label: 'Thứ Ba' },
  { index: 3, label: 'Thứ Tư' },
  { index: 4, label: 'Thứ Năm' },
  { index: 5, label: 'Thứ Sáu' },
  { index: 6, label: 'Thứ Bảy' },
  { index: 7, label: 'Chủ Nhật' },
];
const COLOR_PALETTE = ['#2563eb', '#14b8a6', '#f59e0b', '#8b5cf6', '#ef4444', '#10b981', '#0ea5e9'];
const EMPTY_ITEMS = [];
const GRID_STEP_MINUTES = 15;
const DEFAULT_GRID_START = 7 * 60;
const DEFAULT_GRID_END = 22 * 60;

const zonedToday = () => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
};

const shiftDate = (date, days) => {
  const [year, month, day] = date.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return shifted.toISOString().slice(0, 10);
};

const mondayOf = (date) => {
  const [year, month, day] = date.split('-').map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return shiftDate(date, weekday === 0 ? -6 : 1 - weekday);
};

const formatDate = (date) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'UTC',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
}).format(new Date(`${date}T00:00:00Z`));

const formatTimeRange = (start, end) => `${start || '--:--'} - ${end || '--:--'}`;

const StudentTimetable = () => {
  const toast = useToast();
  const [semesters, setSemesters] = useState([]);
  const [semesterId, setSemesterId] = useState('');
  const [selectedDate, setSelectedDate] = useState(zonedToday);
  const [weekStart, setWeekStart] = useState(() => mondayOf(zonedToday()));
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [response, setResponse] = useState(null);
  const [loadedRequestKey, setLoadedRequestKey] = useState('');
  const [loadingSemesters, setLoadingSemesters] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const requestKey = `${semesterId}:${weekStart}:${page}:${size}:${reloadKey}`;
  const loading = requestKey !== loadedRequestKey;

  useEffect(() => {
    let active = true;
    semesterApi.getAll()
      .then(({ data }) => {
        if (active) setSemesters(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (active) toast.error('Không tải được danh sách học kỳ', 'Vui lòng thử lại sau.');
      })
      .finally(() => {
        if (active) setLoadingSemesters(false);
      });
    return () => { active = false; };
  }, [toast]);

  useEffect(() => {
    let active = true;
    timetableApi.getWeek({ semesterId, weekStart, page, size })
      .then(({ data }) => {
        if (!active) return;
        setResponse(data);
        setError(false);
        setLoadedRequestKey(requestKey);
        if (data.page && data.page !== page) setPage(data.page);
      })
      .catch((requestError) => {
        if (!active) return;
        setResponse(null);
        setError(true);
        setLoadedRequestKey(requestKey);
        toast.error('Không tải được thời khóa biểu', getErrorMessage(requestError));
      })
    return () => { active = false; };
  }, [semesterId, weekStart, page, size, reloadKey, requestKey, toast]);

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') {
        setReloadKey((value) => value + 1);
      }
    };
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, []);

  const items = response?.items || EMPTY_ITEMS;
  const unscheduledSections = response?.unscheduledSections || EMPTY_ITEMS;
  const conflictIds = useMemo(() => {
    const conflicts = new Set();
    for (let left = 0; left < items.length; left += 1) {
      for (let right = left + 1; right < items.length; right += 1) {
        const a = items[left];
        const b = items[right];
        if (a.date !== b.date) continue;
        if (a.startTime < b.endTime && b.startTime < a.endTime) {
          conflicts.add(a.id);
          conflicts.add(b.id);
        }
      }
    }
    return conflicts;
  }, [items]);

  const weekEnd = response?.weekEnd || shiftDate(weekStart, 6);
  const totalElements = Number(response?.totalElements || 0);
  const updateWeek = (date) => {
    setSelectedDate(date);
    setWeekStart(mondayOf(date));
    setPage(1);
  };

  const changeSemester = (value) => {
    setSemesterId(value);
    const today = zonedToday();
    setSelectedDate(today);
    setWeekStart(mondayOf(today));
    setPage(1);
  };

  const dates = useMemo(() => DAYS.map((day) => ({
    ...day,
    date: shiftDate(weekStart, day.index - 1),
  })), [weekStart]);
  const gridBounds = useMemo(() => {
    const starts = items.map((item) => {
      const [hour, minute] = (item.startTime || '07:00').split(':').map(Number);
      return hour * 60 + minute;
    });
    const ends = items.map((item) => {
      const [hour, minute] = (item.endTime || '22:00').split(':').map(Number);
      return hour * 60 + minute;
    });
    const start = Math.min(DEFAULT_GRID_START, ...starts);
    const end = Math.max(DEFAULT_GRID_END, ...ends);
    return {
      start: Math.floor(start / GRID_STEP_MINUTES) * GRID_STEP_MINUTES,
      end: Math.ceil(end / GRID_STEP_MINUTES) * GRID_STEP_MINUTES,
    };
  }, [items]);
  const timeLabels = useMemo(() => {
    const labels = [];
    const firstHour = Math.ceil(gridBounds.start / 60) * 60;
    for (let minutes = firstHour; minutes < gridBounds.end; minutes += 60) {
      labels.push({
        value: `${String(Math.floor(minutes / 60)).padStart(2, '0')}:00`,
        row: 2 + Math.floor((minutes - gridBounds.start) / GRID_STEP_MINUTES),
      });
    }
    return labels;
  }, [gridBounds]);
  const gridSlots = (gridBounds.end - gridBounds.start) / GRID_STEP_MINUTES;
  const getEventGridRow = (item) => {
    const [startHour, startMinute] = (item.startTime || '07:00').split(':').map(Number);
    const [endHour, endMinute] = (item.endTime || '07:15').split(':').map(Number);
    const start = startHour * 60 + startMinute;
    const end = endHour * 60 + endMinute;
    const firstSlot = Math.max(0, Math.floor((start - gridBounds.start) / GRID_STEP_MINUTES));
    const lastSlot = Math.min(gridSlots, Math.ceil((end - gridBounds.start) / GRID_STEP_MINUTES));
    return `${firstSlot + 2} / span ${Math.max(1, lastSlot - firstSlot)}`;
  };

  return (
    <>
      <PageHeader
        title="Thời khóa biểu"
        subtitle="Xem lịch học thực tế theo tuần và học kỳ."
        actions={(
          <div className="page-head-actions">
            <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
              In
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setReloadKey((value) => value + 1)}>
              Làm mới
            </button>
            <Link to="/student/enrollments" className="btn btn-secondary">Học phần đã đăng ký</Link>
            <Link to="/student/courses" className="btn btn-primary">Đăng ký thêm</Link>
          </div>
        )}
      />

      <section className="card timetable-card">
        <div className="card-head timetable-toolbar">
          <div className="timetable-week-navigation">
            <button type="button" className="btn btn-secondary" onClick={() => updateWeek(shiftDate(weekStart, -7))}>
              Tuần trước
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => updateWeek(zonedToday())}>
              Hôm nay
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => updateWeek(shiftDate(weekStart, 7))}>
              Tuần sau
            </button>
            <strong>{formatDate(weekStart)} – {formatDate(weekEnd)}</strong>
          </div>
          <div className="timetable-filters">
            <label>
              <span>Chọn ngày</span>
              <input
                className="input"
                type="date"
                value={selectedDate}
                onChange={(event) => event.target.value && updateWeek(event.target.value)}
              />
            </label>
            <label>
              <span>Học kỳ</span>
              <select
                className="select"
                value={semesterId}
                onChange={(event) => changeSemester(event.target.value)}
                disabled={loadingSemesters}
              >
                <option value="">Tất cả học kỳ</option>
                {semesters.map((semester) => (
                  <option key={semester.id} value={semester.id}>
                    {semester.semesterName} ({semester.semesterCode})
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <div className="card-body">
          {loading ? (
            <LoadingBlock />
          ) : error ? (
            <EmptyState
              icon={<CalendarIcon />}
              title="Không tải được lịch học"
              description="Có lỗi khi kết nối máy chủ. Hãy thử tải lại."
              action={<button className="btn btn-primary" onClick={() => setReloadKey((value) => value + 1)}>Thử lại</button>}
            />
          ) : totalElements === 0 && unscheduledSections.length === 0 ? (
            <EmptyState
              icon={<CalendarIcon />}
              title="Tuần này chưa có lịch học."
              description="Không có lịch học đã được xác nhận trong tuần và học kỳ đang chọn."
            />
          ) : (
            <>
              {unscheduledSections.length > 0 && (
                <div className="timetable-unscheduled" role="status">
                  <strong>Chưa có lịch học cho lớp:</strong>{' '}
                  {unscheduledSections.map((section) => section.sectionCode || section.courseName).filter(Boolean).join(', ')}
                </div>
              )}
              {totalElements > 0 && (
                <div className="timetable-week-scroll">
                  <div
                    className="timetable-week-grid"
                    style={{
                      gridTemplateColumns: '58px repeat(7, minmax(150px, 1fr)) 58px',
                      gridTemplateRows: `48px repeat(${gridSlots}, 12px)`,
                    }}
                  >
                    {dates.map((day) => (
                      <header
                        key={day.index}
                        className="timetable-week-heading"
                        style={{ gridColumn: day.index + 1, gridRow: 1 }}
                      >
                        <strong>{day.label}</strong>
                        <span>{formatDate(day.date)}</span>
                      </header>
                    ))}
                    <div
                      className="timetable-hour-column"
                      style={{ gridColumn: 1, gridRow: `2 / span ${gridSlots}` }}
                    />
                    {dates.map((day) => (
                      <div
                        key={`column-${day.index}`}
                        className="timetable-week-column"
                        style={{ gridColumn: day.index + 1, gridRow: `2 / span ${gridSlots}` }}
                      />
                    ))}
                    <div
                      className="timetable-hour-column"
                      style={{ gridColumn: 9, gridRow: `2 / span ${gridSlots}` }}
                    />
                    {timeLabels.map((label) => (
                      <React.Fragment key={label.row}>
                        <span className="timetable-hour-label timetable-hour-label-left" style={{ gridColumn: 1, gridRow: label.row }}>
                          {label.value}
                        </span>
                        <span className="timetable-hour-label timetable-hour-label-right" style={{ gridColumn: 9, gridRow: label.row }}>
                          {label.value}
                        </span>
                      </React.Fragment>
                    ))}
                    {items.map((item) => (
                      <article
                        key={item.id}
                        className={`timetable-week-event ${conflictIds.has(item.id) ? 'has-conflict' : ''}`}
                        style={{
                          gridColumn: item.dayOfWeek + 1,
                          gridRow: getEventGridRow(item),
                          borderLeftColor: conflictIds.has(item.id)
                            ? '#f59e0b'
                            : COLOR_PALETTE[(item.sectionCode?.length || 0) % COLOR_PALETTE.length],
                        }}
                        title={`${item.courseName || 'Môn học'} · ${item.sectionCode || '—'}`}
                      >
                        <strong>{item.courseName || 'Môn học'}</strong>
                        <span>{item.sectionCode || '—'} · {item.courseCode || '—'}</span>
                        <span>{item.room || 'Phòng chưa cập nhật'}</span>
                        <span>GV: {item.lecturerName || 'Chưa phân công'}</span>
                        {item.startPeriod && item.endPeriod && (
                          <span>Tiết {item.startPeriod}–{item.endPeriod}</span>
                        )}
                        <span><ClockIcon size={12} /> {formatTimeRange(item.startTime, item.endTime)}</span>
                        {conflictIds.has(item.id) && <b>Trùng lịch</b>}
                      </article>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {!loading && !error && (
          <Pagination
            page={page}
            pageSize={size}
            totalItems={totalElements}
            pageSizeOptions={PAGE_SIZE_OPTIONS}
            onPageChange={setPage}
            onPageSizeChange={(value) => { setSize(value); setPage(1); }}
          />
        )}
      </section>
    </>
  );
};

export default StudentTimetable;
