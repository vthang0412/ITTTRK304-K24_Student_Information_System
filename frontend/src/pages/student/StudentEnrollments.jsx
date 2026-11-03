import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { enrollmentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CalendarIcon, ClipboardIcon, ClockIcon, UserIcon } from '../../components/Icons';
import {
  ConfirmDialog, EmptyState, LoadingBlock, PageHeader, Pagination, SearchInput, StatusBadge,
} from '../../components/UI';
import { formatDateTime } from '../../utils/format';

const PAGE_SIZE = 8;

const StudentEnrollments = () => {
  const toast = useToast();
  const [enrollments, setEnrollments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);
  const [target, setTarget] = useState(null);
  const [withdrawing, setWithdrawing] = useState(false);

  const load = async () => {
    try {
      const res = await enrollmentApi.getMyEnrollments();
      setEnrollments(res.data || []);
    } catch {
      toast.error('Không tải được dữ liệu', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return enrollments.filter((e) => {
      if (status !== 'ALL' && e.status !== status) return false;
      if (!keyword) return true;
      return [e.courseSection?.sectionCode, e.courseSection?.course?.courseName, e.courseSection?.course?.courseCode]
        .filter(Boolean).some((v) => v.toLowerCase().includes(keyword));
    });
  }, [enrollments, search, status]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const activeCount = enrollments.filter((e) => e.status === 'ENROLLED').length;
  const totalCredits = enrollments
    .filter((e) => e.status === 'ENROLLED')
    .reduce((sum, e) => sum + Number(e.courseSection?.course?.credits || 0), 0);

  const handleWithdraw = async () => {
    if (!target) return;
    setWithdrawing(true);
    try {
      await enrollmentApi.withdraw(target.id);
      toast.success('Huỷ đăng ký thành công', `Đã huỷ lớp ${target.courseSection?.sectionCode}.`);
      setTarget(null);
      await load();
    } catch (err) {
      toast.error('Huỷ đăng ký thất bại', err.response?.data?.message || 'Vui lòng thử lại sau.');
    } finally {
      setWithdrawing(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Học phần đã đăng ký"
        subtitle="Theo dõi các học phần bạn đã đăng ký trong học kỳ."
        actions={<Link to="/student/courses" className="btn btn-primary">Đăng ký thêm</Link>}
      />

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon tone-blue"><ClipboardIcon /></div>
          <div className="stat-info"><small>Tổng đăng ký</small><strong>{enrollments.length}</strong></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon tone-green"><CalendarIcon /></div>
          <div className="stat-info"><small>Đang học</small><strong>{activeCount}</strong></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon tone-amber"><ClockIcon /></div>
          <div className="stat-info"><small>Tín chỉ đang học</small><strong>{totalCredits}</strong></div>
        </div>
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo môn học hoặc mã lớp..." />
        <select className="select" style={{ width: 180 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="ENROLLED">Đã đăng ký</option>
          <option value="WITHDRAWN">Đã huỷ</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} kết quả</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<ClipboardIcon />}
            title="Chưa có học phần nào"
            description="Bạn chưa đăng ký học phần nào phù hợp với bộ lọc hiện tại."
            action={<Link to="/student/courses" className="btn btn-primary">Khám phá lớp học phần</Link>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Môn học</th>
                    <th>Mã lớp</th>
                    <th>Giảng viên</th>
                    <th>Lịch học</th>
                    <th>Tín chỉ</th>
                    <th>Ngày đăng ký</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((e) => (
                    <tr key={e.id}>
                      <td>
                        <div className="cell-strong">{e.courseSection?.course?.courseName || '—'}</div>
                        <div className="cell-muted">{e.courseSection?.semester?.semesterName || '—'}</div>
                      </td>
                      <td>{e.courseSection?.sectionCode || '—'}</td>
                      <td className="cell-muted">
                        <div className="flex" style={{ gap: 6 }}><UserIcon size={14} />{e.courseSection?.lecturer?.fullName || 'Chưa phân công'}</div>
                      </td>
                      <td className="cell-muted">{e.courseSection?.schedule || '—'}</td>
                      <td>{e.courseSection?.course?.credits ?? '—'}</td>
                      <td className="cell-muted">{formatDateTime(e.registeredAt)}</td>
                      <td><StatusBadge status={e.status} /></td>
                      <td>
                        <div className="row-actions">
                          {e.status === 'ENROLLED' ? (
                            <button className="btn btn-secondary btn-sm" onClick={() => setTarget(e)}>Huỷ đăng ký</button>
                          ) : (
                            <span className="cell-muted">—</span>
                          )}
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

      <ConfirmDialog
        open={!!target}
        title="Huỷ đăng ký học phần"
        message={`Bạn có chắc muốn huỷ đăng ký lớp ${target?.courseSection?.sectionCode} — ${target?.courseSection?.course?.courseName}? Thao tác này không thể hoàn tác.`}
        confirmLabel="Huỷ đăng ký"
        loading={withdrawing}
        onConfirm={handleWithdraw}
        onCancel={() => setTarget(null)}
      />
    </>
  );
};

export default StudentEnrollments;
