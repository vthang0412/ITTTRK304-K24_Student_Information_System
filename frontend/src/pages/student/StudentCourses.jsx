import React, { useEffect, useMemo, useState } from 'react';
import { courseSectionApi, enrollmentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  BookIcon, CalendarIcon, ClockIcon, LayersIcon, PinIcon, SearchIcon, UserIcon,
} from '../../components/Icons';
import {
  EmptyState, LoadingBlock, PageHeader, Pagination, SearchInput, StatusBadge,
} from '../../components/UI';

const PAGE_SIZE = 8;

const StudentCourses = () => {
  const toast = useToast();
  const [sections, setSections] = useState([]);
  const [enrolledIds, setEnrolledIds] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [enrollingId, setEnrollingId] = useState(null);
  const [search, setSearch] = useState('');
  const [semester, setSemester] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);

  const load = async () => {
    try {
      const [sectionsRes, enrollRes] = await Promise.all([
        courseSectionApi.getAll(),
        enrollmentApi.getMyEnrollments(),
      ]);
      setSections(sectionsRes.data || []);
      setEnrolledIds(new Set((enrollRes.data || []).filter((e) => e.status === 'ENROLLED').map((e) => e.courseSection?.id)));
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

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const handleEnroll = async (section) => {
    setEnrollingId(section.id);
    try {
      await enrollmentApi.enroll(section.id);
      setEnrolledIds((prev) => new Set(prev).add(section.id));
      toast.success('Đăng ký thành công', `Bạn đã đăng ký lớp ${section.sectionCode}.`);
    } catch (err) {
      const message = err.response?.data?.message;
      const map = {
        'Already enrolled in this section': 'Bạn đã đăng ký lớp học phần này.',
        'Course section is full': 'Lớp học phần đã đủ sĩ số.',
        'Course section is not open for registration': 'Lớp học phần chưa mở đăng ký.',
      };
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
                    const isEnrolled = enrolledIds.has(s.id);
                    const disabled = isEnrolled || s.status !== 'OPEN' || enrollingId === s.id;
                    return (
                      <tr key={s.id}>
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
                        <td>{s.capacity}</td>
                        <td><StatusBadge status={s.status} /></td>
                        <td>
                          <div className="row-actions">
                            {isEnrolled ? (
                              <span className="badge badge-green">Đã đăng ký</span>
                            ) : (
                              <button
                                className="btn btn-primary btn-sm"
                                disabled={disabled}
                                onClick={() => handleEnroll(s)}
                              >
                                {enrollingId === s.id ? 'Đang đăng ký...' : 'Đăng ký'}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination page={page} pageSize={PAGE_SIZE} totalItems={filtered.length} onPageChange={setPage} />
          </>
        )}
      </div>

      <div className="card mt-16" style={{ background: 'var(--blue-50)', borderColor: '#d6e6ff' }}>
        <div className="card-body flex" style={{ gap: 12 }}>
          <BookIcon style={{ color: 'var(--blue-600)', flex: '0 0 auto' }} />
          <p className="small" style={{ color: 'var(--text-2)' }}>
            Lưu ý: Bạn chỉ có thể đăng ký lớp đang ở trạng thái <strong>Đang mở</strong> và còn sĩ số.
            Sau khi đăng ký, lớp sẽ xuất hiện trong mục <strong>Học phần đã đăng ký</strong>.
          </p>
        </div>
      </div>
    </>
  );
};

export default StudentCourses;
