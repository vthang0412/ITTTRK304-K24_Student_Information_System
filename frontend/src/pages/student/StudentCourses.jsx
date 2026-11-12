import React, { useEffect, useMemo, useState } from 'react';
import { courseSectionApi, enrollmentApi } from '../../api';
import { usePaginatedList } from '../../hooks/usePaginatedList';
import { useToast } from '../../context/ToastContext';
import {
  BookIcon, ClockIcon, LayersIcon, SearchIcon, UserIcon,
} from '../../components/Icons';
import {
  EmptyState, LoadingBlock, PageHeader, Pagination, SearchInput, StatusBadge,
} from '../../components/UI';

const StudentCourses = () => {
  const toast = useToast();
  const [sections, setSections] = useState([]);
  const [sectionStatus, setSectionStatus] = useState({});
  const [loading, setLoading] = useState(true);
  const [enrollingId, setEnrollingId] = useState(null);
  const [search, setSearch] = useState('');
  const [semester, setSemester] = useState('ALL');
  const [status, setStatus] = useState('ALL');

  const load = async () => {
    try {
      const [sectionsRes, enrollRes] = await Promise.all([
        courseSectionApi.getAll(),
        enrollmentApi.getMyEnrollments(),
      ]);
      setSections(sectionsRes.data || []);
      const activeStatuses = {};
      (enrollRes.data || [])
        .filter((e) => e.status === 'ENROLLED' || e.status === 'PENDING')
        .forEach((e) => { activeStatuses[e.courseSection?.id] = e.status; });
      setSectionStatus(activeStatuses);
    } catch {
      toast.error('Không tải được danh sách lớp', 'Vui lòng kiểm tra kết nối tới máy chủ.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const semesters = useMemo(() => {
    const map = new Map();
    sections.forEach((s) => { if (s.semester) map.set(s.semester.id, s.semester); });
    return Array.from(map.values());
  }, [sections]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return sections.filter((s) => {
      if (semester !== 'ALL' && String(s.semester?.id) !== semester) return false;
      if (status !== 'ALL' && s.status !== status) return false;
      if (!keyword) return true;
      return [s.sectionCode, s.course?.courseName, s.course?.courseCode, s.lecturer?.fullName, s.schedule]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [sections, search, semester, status]);

  const { page, setPage, pageSize, setPageSize, totalItems, pageItems } = usePaginatedList(filtered);

  const handleEnroll = async (section) => {
    setEnrollingId(section.id);
    try {
      await enrollmentApi.enroll(section.id);
      setSectionStatus((prev) => ({ ...prev, [section.id]: 'PENDING' }));
      toast.success('Đã gửi đơn đăng ký', `Lớp ${section.sectionCode} đang chờ quản trị viên duyệt.`);
      await load();
    } catch (err) {
      const message = err.response?.data?.message;
      const map = {
        'Already enrolled in this section': 'Bạn đã đăng ký lớp học phần này.',
        'Course section is full': 'Lớp học phần đã đủ sĩ số.',
        'Lớp học phần đã đủ số lượng đăng ký': 'Lớp học phần đã đủ sĩ số.',
        'Course section is not open for registration': 'Lớp học phần chưa mở đăng ký.',
      };
      if (message?.includes('đủ số lượng')) await load();
      toast.error('Đăng ký thất bại', map[message] || message || 'Vui lòng thử lại sau.');
    } finally {
      setEnrollingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Đăng ký học phần"
        subtitle="Chọn các lớp học phần đang mở để đăng ký trong học kỳ hiện tại."
        actions={<span className="chip"><LayersIcon size={15} /> {filtered.length} lớp học phần</span>}
      />

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo môn học, mã lớp, giảng viên..." />
        <select className="select" style={{ width: 220 }} value={semester} onChange={(e) => { setSemester(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả học kỳ</option>
          {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName}</option>)}
        </select>
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="OPEN">Đang mở</option>
          <option value="CLOSED">Đã đóng</option>
          <option value="UPCOMING">Sắp diễn ra</option>
        </select>
        <div className="toolbar-spacer" />
        <button className="btn btn-secondary" onClick={() => { setSearch(''); setSemester('ALL'); setStatus('ALL'); setPage(1); }}>
          Đặt lại
        </button>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<SearchIcon />}
            title="Không tìm thấy lớp học phần"
            description="Thử thay đổi từ khoá tìm kiếm hoặc bộ lọc học kỳ."
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã lớp</th>
                    <th>Môn học</th>
                    <th>Giảng viên</th>
                    <th>Lịch học</th>
                    <th>Sĩ số</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) => {
                    const currentStatus = sectionStatus[s.id];
                    const isEnrolled = currentStatus === 'ENROLLED';
                    const isPending = currentStatus === 'PENDING';
                    const capacity = Number(s.capacity) > 0 ? Number(s.capacity) : 40;
                    const registeredCount = Number(s.registeredCount) || 0;
                    const isFull = registeredCount >= capacity;
                    const disabled = Boolean(currentStatus) || s.status !== 'OPEN' || isFull || enrollingId === s.id;
                    return (
                      <tr key={s.id} className={isFull ? 'course-section-full' : undefined}>
                        <td className="cell-strong">{s.sectionCode}</td>
                        <td>
                          <div className="cell-strong">{s.course?.courseName || '—'}</div>
                          <div className="cell-muted">{s.course?.courseCode} · {s.course?.credits} tín chỉ · {s.semester?.semesterName}</div>
                        </td>
                        <td>
                          <div className="flex" style={{ gap: 6 }}>
                            <UserIcon size={15} style={{ color: 'var(--muted)' }} />
                            {s.lecturer?.fullName || 'Chưa phân công'}
                          </div>
                        </td>
                        <td>
                          <div className="schedule-line"><ClockIcon size={14} /> {s.schedule || 'Chưa xếp lịch'}</div>
                        </td>
                        <td>{registeredCount}/{capacity}</td>
                        <td>
                          <div className="course-section-status">
                            <StatusBadge status={s.status} />
                            {isFull && <StatusBadge status="CLOSED" label="Đã đủ sĩ số" />}
                          </div>
                        </td>
                        <td>
                          <div className="row-actions">
                            {isEnrolled ? (
                              <span className="badge badge-green">Đã đăng ký</span>
                            ) : isPending ? (
                              <span className="badge badge-amber">Chờ duyệt</span>
                            ) : (
                              isFull ? (
                                <span className="badge badge-gray">Hết chỗ</span>
                              ) : (
                                <button
                                  className="btn btn-primary btn-sm"
                                  disabled={disabled}
                                  onClick={() => handleEnroll(s)}
                                >
                                  {enrollingId === s.id ? 'Đang gửi...' : 'Đăng ký'}
                                </button>
                              )
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination page={page} pageSize={pageSize} totalItems={totalItems} onPageChange={setPage} onPageSizeChange={setPageSize} />
          </>
        )}
      </div>

      <div className="card mt-16" style={{ background: 'var(--blue-50)', borderColor: '#d6e6ff' }}>
        <div className="card-body flex" style={{ gap: 12 }}>
          <BookIcon style={{ color: 'var(--blue-600)', flex: '0 0 auto' }} />
          <p className="small" style={{ color: 'var(--text-2)' }}>
            Lưu ý: Bạn chỉ có thể đăng ký lớp đang ở trạng thái <strong>Đang mở</strong> và còn sĩ số.
            Sau khi gửi đơn, lớp sẽ ở trạng thái <strong>Chờ duyệt</strong> cho đến khi quản trị viên phê duyệt
            và mới xuất hiện trong <strong>Thời khóa biểu</strong>.
          </p>
        </div>
      </div>
    </>
  );
};

export default StudentCourses;
