import React, { useEffect, useMemo, useState } from 'react';
import { courseSectionApi, lecturerApi, semesterApi, timetableApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CalendarIcon, ChevronLeftIcon, ChevronRightIcon, ClockIcon } from '../../components/Icons';
import { EmptyState, LoadingBlock, PageHeader, Pagination } from '../../components/UI';
import { getErrorMessage } from '../../utils/format';
import { PAGE_SIZE_OPTIONS } from '../../hooks/usePaginatedList';

const EMPTY_ITEMS = [];
const DAYS = [
  { index: 1, label: 'Thứ 2' },
  { index: 2, label: 'Thứ 3' },
  { index: 3, label: 'Thứ 4' },
  { index: 4, label: 'Thứ 5' },
  { index: 5, label: 'Thứ 6' },
  { index: 6, label: 'Thứ 7' },
  { index: 7, label: 'Chủ Nhật' },
];

const todayInVietnam = () => {
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
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
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

const AdminTimetable = () => {
  const toast = useToast();
  const [semesters, setSemesters] = useState([]);
  const [sections, setSections] = useState([]);
  const [lecturers, setLecturers] = useState([]);
  const [filtersLoading, setFiltersLoading] = useState(true);
  const [weekStart, setWeekStart] = useState(() => mondayOf(todayInVietnam()));
  const [semesterId, setSemesterId] = useState('');
  const [sectionId, setSectionId] = useState('');
  const [lecturerId, setLecturerId] = useState('');
  const [classroom, setClassroom] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [response, setResponse] = useState(null);
  const [loadedRequestKey, setLoadedRequestKey] = useState('');
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const requestKey = `${semesterId}:${sectionId}:${lecturerId}:${classroom.trim()}:${weekStart}:${page}:${pageSize}:${retry}`;
  const loading = requestKey !== loadedRequestKey;

  useEffect(() => {
    let active = true;
    Promise.all([semesterApi.getAll(), courseSectionApi.getAll(), lecturerApi.getAll()])
      .then(([semesterResponse, sectionResponse, lecturerResponse]) => {
        if (!active) return;
        setSemesters(Array.isArray(semesterResponse.data) ? semesterResponse.data : []);
        setSections(Array.isArray(sectionResponse.data) ? sectionResponse.data : []);
        setLecturers(Array.isArray(lecturerResponse.data) ? lecturerResponse.data : []);
      })
      .catch((requestError) => {
        if (active) toast.error('Không tải được bộ lọc thời khóa biểu', getErrorMessage(requestError));
      })
      .finally(() => {
        if (active) setFiltersLoading(false);
      });
    return () => { active = false; };
  }, [toast]);

  useEffect(() => {
    let active = true;
    timetableApi.getAdminWeek({
      semesterId,
      sectionId,
      lecturerId,
      classroom: classroom.trim(),
      weekStart,
      page,
      size: pageSize,
    })
      .then(({ data }) => {
        if (!active) return;
        setResponse(data);
        setError('');
        setPage(data.page || 1);
        setLoadedRequestKey(requestKey);
      })
      .catch((requestError) => {
        if (!active) return;
        setResponse(null);
        setError(getErrorMessage(requestError));
        setLoadedRequestKey(requestKey);
      });
    return () => { active = false; };
  }, [semesterId, sectionId, lecturerId, classroom, weekStart, page, pageSize, retry, requestKey]);

  const dates = useMemo(() => DAYS.map((day) => ({
    ...day,
    date: shiftDate(weekStart, day.index - 1),
  })), [weekStart]);
  const items = response?.items || EMPTY_ITEMS;
  const byDay = useMemo(() => {
    const groups = new Map(DAYS.map((day) => [day.index, []]));
    items.forEach((item) => groups.get(item.dayOfWeek)?.push(item));
    groups.forEach((dayItems) => dayItems.sort((left, right) => left.startTime.localeCompare(right.startTime)));
    return groups;
  }, [items]);
  const totalElements = Number(response?.totalElements || 0);

  const updateFilter = (setter) => (event) => {
    setter(event.target.value);
    setPage(1);
  };
  const moveWeek = (offset) => {
    setWeekStart((current) => shiftDate(current, offset * 7));
    setPage(1);
  };
  const selectedSections = semesterId
    ? sections.filter((section) => String(section.semester?.id) === semesterId)
    : sections;

  return (
    <>
      <PageHeader title="Thời khóa biểu toàn trường" subtitle="Xem các buổi học thực tế theo tuần và lọc theo học kỳ, lớp, giảng viên hoặc phòng." />
      <section className="card">
        <div className="card-body">
          <div className="timetable-week-toolbar">
            <div className="timetable-week-navigation">
              <button type="button" className="btn btn-secondary" onClick={() => moveWeek(-1)} aria-label="Tuần trước">
                <ChevronLeftIcon /> Tuần trước
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => { setWeekStart(mondayOf(todayInVietnam())); setPage(1); }}>
                Tuần hiện tại
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => moveWeek(1)} aria-label="Tuần tiếp theo">
                Tuần sau <ChevronRightIcon />
              </button>
            </div>
            <strong>{formatDate(weekStart)} – {formatDate(shiftDate(weekStart, 6))}</strong>
          </div>
          <div className="toolbar admin-timetable-filters">
            <select className="select" value={semesterId} onChange={updateFilter(setSemesterId)} disabled={filtersLoading}>
              <option value="">Tất cả học kỳ</option>
              {semesters.map((semester) => (
                <option key={semester.id} value={semester.id}>{semester.semesterName} ({semester.semesterCode})</option>
              ))}
            </select>
            <select className="select" value={sectionId} onChange={updateFilter(setSectionId)} disabled={filtersLoading}>
              <option value="">Tất cả lớp học phần</option>
              {selectedSections.map((section) => (
                <option key={section.id} value={section.id}>{section.sectionCode} · {section.course?.courseName}</option>
              ))}
            </select>
            <select className="select" value={lecturerId} onChange={updateFilter(setLecturerId)} disabled={filtersLoading}>
              <option value="">Tất cả giảng viên</option>
              {lecturers.map((lecturer) => (
                <option key={lecturer.id} value={lecturer.id}>{lecturer.fullName}</option>
              ))}
            </select>
            <input
              className="input"
              value={classroom}
              onChange={(event) => { setClassroom(event.target.value); setPage(1); }}
              placeholder="Lọc theo phòng học"
              aria-label="Lọc theo phòng học"
            />
          </div>
        </div>
        <div className="card-body">
          {loading ? <LoadingBlock text="Đang tải thời khóa biểu..." /> : error ? (
            <EmptyState
              icon={<CalendarIcon />}
              title="Không tải được thời khóa biểu"
              description={error}
              action={<button className="btn btn-primary" onClick={() => setRetry((current) => current + 1)}>Thử lại</button>}
            />
          ) : totalElements === 0 ? (
            <EmptyState
              icon={<CalendarIcon />}
              title="Tuần này chưa có lịch học"
              description="Không có buổi học thực tế khớp với tuần và bộ lọc đã chọn."
            />
          ) : (
            <div className="admin-timetable-week">
              {dates.map((day) => (
                <section className="admin-timetable-day" key={day.index}>
                  <header>
                    <strong>{day.label}</strong>
                    <span>{formatDate(day.date)}</span>
                  </header>
                  <div className="admin-timetable-day-events">
                    {(byDay.get(day.index) || []).map((item) => (
                      <article key={item.id} className="admin-timetable-event">
                        <strong>{item.courseName || item.courseCode || 'Môn học'}</strong>
                        <span>{item.courseCode || '—'} · {item.sectionCode}</span>
                        <span>{item.lecturerName || 'Chưa phân công'}</span>
                        <span>{item.room || 'Chưa cập nhật phòng'}</span>
                        {item.startPeriod && <span>Tiết {item.startPeriod}–{item.endPeriod}</span>}
                        <span><ClockIcon size={13} /> {item.startTime}–{item.endTime}</span>
                      </article>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          )}
          {!loading && !error && (
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalElements}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              onPageChange={setPage}
              onPageSizeChange={(value) => { setPageSize(value); setPage(1); }}
            />
          )}
        </div>
      </section>
    </>
  );
};

export default AdminTimetable;
