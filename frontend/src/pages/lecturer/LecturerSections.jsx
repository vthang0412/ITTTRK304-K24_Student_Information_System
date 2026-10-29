import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { courseSectionApi, gradeApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { BookIcon, ClockIcon, LayersIcon, StudentsIcon, UserIcon } from '../../components/Icons';
import {
  EmptyState, LoadingBlock, PageHeader, Pagination, SearchInput, StatCard, StatusBadge,
} from '../../components/UI';

const PAGE_SIZE = 8;

const LecturerSections = () => {
  const toast = useToast();
  const [sections, setSections] = useState([]);
  const [counts, setCounts] = useState({});
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        let res;
        try {
          res = await courseSectionApi.getMine();
        } catch {
          res = await courseSectionApi.getAll();
        }
        if (!mounted) return;
        const list = res.data || [];
        setSections(list);

        const results = await Promise.allSettled(list.map((s) => gradeApi.getGradesBySection(s.id)));
        if (!mounted) return;
        const nextCounts = {};
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') {
            nextCounts[list[index].id] = (result.value.data || []).filter((e) => e.status === 'ENROLLED').length;
          }
        });
        setCounts(nextCounts);
      } catch {
        if (mounted) toast.error('Không tải được lớp học phần', 'Vui lòng thử lại sau.');
      } finally {
        if (mounted) setLoading(false);
      }
    };
    load();
    return () => { mounted = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return sections.filter((s) => {
      if (status !== 'ALL' && s.status !== status) return false;
      if (!keyword) return true;
      return [s.sectionCode, s.course?.courseName, s.course?.courseCode, s.semester?.semesterName, s.schedule]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [sections, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const totalStudents = Object.values(counts).reduce((sum, v) => sum + v, 0);
  const openCount = sections.filter((s) => s.status === 'OPEN').length;

  return (
    <>
      <PageHeader
        title="Lớp học phần"
        subtitle="Danh sách lớp học phần bạn được phân công giảng dạy."
        actions={<Link to="/lecturer/grades" className="btn btn-primary">Nhập điểm</Link>}
      />

      <div className="stats-grid">
        <StatCard label="Lớp được phân công" value={sections.length} icon={<LayersIcon />} tone="blue" />
        <StatCard label="Lớp đang mở" value={openCount} icon={<BookIcon />} tone="green" />
        <StatCard label="Sinh viên đã đăng ký" value={totalStudents} icon={<StudentsIcon />} tone="amber" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo môn học, mã lớp, học kỳ..." />
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="OPEN">Đang mở</option>
          <option value="CLOSED">Đã đóng</option>
          <option value="UPCOMING">Sắp diễn ra</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} lớp</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<LayersIcon />}
            title="Chưa có lớp học phần"
            description="Bạn chưa được phân công lớp học phần nào trong học kỳ này."
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
                        <div className="schedule-line"><ClockIcon size={14} />{s.schedule || 'Chưa xếp lịch'}</div>
                      </td>
                      <td>
                        <div className="flex" style={{ gap: 6 }}>
                          <UserIcon size={14} style={{ color: 'var(--muted)' }} />
                          {counts[s.id] ?? 0} / {s.capacity ?? '—'}
                        </div>
                      </td>
                      <td><StatusBadge status={s.status} /></td>
                      <td>
                        <div className="row-actions">
                          <Link to={`/lecturer/grades?section=${s.id}`} className="btn btn-secondary btn-sm">Nhập điểm</Link>
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
    </>
  );
};

export default LecturerSections;
