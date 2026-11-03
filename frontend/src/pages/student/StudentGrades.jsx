import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { studentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { AwardIcon, BookIcon, EyeIcon, TrendingIcon } from '../../components/Icons';
import {
  EmptyState, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard,
} from '../../components/UI';
import { formatDateTime, formatScore, getErrorMessage, gradeLabel, passesCourse } from '../../utils/format';

const PAGE_SIZE = 10;

const StudentGrades = () => {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [detail, setDetail] = useState(null);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const res = await studentApi.getMyGrades();
        if (mounted) setItems(res.data || []);
      } catch (err) {
        if (mounted) toast.error('Không tải được kết quả', getErrorMessage(err));
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => { mounted = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const stats = useMemo(() => {
    const graded = items.filter((i) => i.finalScore !== null && i.finalScore !== undefined);
    const scores = graded.map((i) => Number(i.finalScore));
    const average = scores.length ? (scores.reduce((s, v) => s + v, 0) / scores.length).toFixed(2) : '—';
    const passed = items.filter((i) => passesCourse(i.finalScore));
    const credits = passed.reduce((sum, i) => sum + Number(i.credits || 0), 0);
    return { average, credits, graded: graded.length, total: items.length };
  }, [items]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return items;
    return items.filter((i) => [i.courseName, i.courseCode, i.sectionCode, i.semesterName]
      .filter(Boolean).some((v) => String(v).toLowerCase().includes(keyword)));
  }, [items, search]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <>
      <PageHeader
        title="Kết quả học tập"
        subtitle="Chỉ hiển thị điểm của các học phần đã được nhà trường công bố."
      />

      <div className="stats-grid">
        <StatCard label="Điểm trung bình" value={stats.average} icon={<TrendingIcon />} tone="blue" />
        <StatCard label="Tín chỉ tích luỹ" value={stats.credits} icon={<AwardIcon />} tone="green" />
        <StatCard label="Học phần đã công bố" value={`${stats.graded}/${stats.total}`} icon={<BookIcon />} tone="amber" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm theo môn học hoặc mã lớp..." />
        <div className="toolbar-spacer" />
        <Link to="/student/enrollments" className="btn btn-secondary">Học phần đã đăng ký</Link>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<AwardIcon />}
            title="Chưa có kết quả được công bố"
            description="Điểm sẽ xuất hiện sau khi giảng viên hoàn tất nhập điểm và nhà trường công bố bảng điểm."
            action={<Link to="/student/enrollments" className="btn btn-primary">Xem học phần của tôi</Link>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Môn học</th>
                    <th>Mã lớp</th>
                    <th>Học kỳ</th>
                    <th>Tín chỉ</th>
                    <th>Thành phần</th>
                    <th>Điểm tổng kết</th>
                    <th>Xếp loại</th>
                    <th>Công bố</th>
                    <th style={{ textAlign: 'right' }}>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => {
                    const gl = gradeLabel(item.finalScore);
                    return (
                      <tr key={item.enrollmentId}>
                        <td>
                          <div className="cell-strong">{item.courseName || '—'}</div>
                          <div className="cell-muted">{item.courseCode}</div>
                        </td>
                        <td>{item.sectionCode || '—'}</td>
                        <td className="cell-muted">{item.semesterName || '—'}</td>
                        <td>{item.credits ?? '—'}</td>
                        <td>
                          {item.componentScores?.length
                            ? <span className="chip">{item.componentScores.length} thành phần</span>
                            : formatScore(item.componentScore)}
                        </td>
                        <td><strong>{formatScore(item.finalScore)}</strong></td>
                        <td><span className={`badge badge-${gl.tone}`}>{gl.label}</span></td>
                        <td className="cell-muted">{formatDateTime(item.publishedAt)}</td>
                        <td>
                          <div className="row-actions">
                            <button className="btn btn-secondary btn-sm" onClick={() => setDetail(item)}>
                              <EyeIcon size={14} /> Xem
                            </button>
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

      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail ? detail.courseName : 'Chi tiết điểm'}
        subtitle={detail ? `${detail.courseCode} · ${detail.sectionCode} · ${detail.semesterName || ''}` : ''}
        size="md"
        footer={<button className="btn btn-secondary" onClick={() => setDetail(null)}>Đóng</button>}
      >
        {detail && (
          <>
            {detail.componentScores?.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Thành phần</th>
                      <th>Trọng số</th>
                      <th>Điểm tối đa</th>
                      <th>Điểm</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.componentScores.map((component) => (
                      <tr key={component.componentId}>
                        <td className="cell-strong">{component.componentName}</td>
                        <td>{formatScore(component.weight)}%</td>
                        <td>{formatScore(component.maxScore)}</td>
                        <td><strong>{formatScore(component.score)}</strong></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="muted">Học phần này được đánh giá bằng điểm tổng kết.</p>
            )}
            <div className="mt-16" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="muted">Công bố lúc: {formatDateTime(detail.publishedAt)}</span>
              <span className="chip">Điểm tổng kết: <strong>{formatScore(detail.finalScore)}</strong></span>
            </div>
          </>
        )}
      </Modal>
    </>
  );
};

export default StudentGrades;
