import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { gradebookApi, semesterApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  AwardIcon, CheckCircleIcon, ClipboardIcon, EyeIcon, FileTextIcon,
  LockIcon, RefreshIcon, UsersIcon,
} from '../../components/Icons';
import {
  Alert, ConfirmDialog, EmptyState, LoadingBlock, Modal, PageHeader, Pagination,
  SearchInput, StatCard, StatusBadge,
} from '../../components/UI';
import { formatDateTime, formatScore, getErrorMessage } from '../../utils/format';

const PAGE_SIZE = 10;

const ACTIONS = {
  lock: { label: 'Khoá bảng điểm', tone: 'danger', title: 'Khoá bảng điểm' },
  unlock: { label: 'Mở khoá bảng điểm', tone: 'danger', title: 'Mở khoá bảng điểm' },
  approve: { label: 'Duyệt bảng điểm', tone: 'primary', title: 'Duyệt bảng điểm' },
  publish: { label: 'Công bố bảng điểm', tone: 'primary', title: 'Công bố bảng điểm' },
};

const ACTION_LABELS = {
  LOCK: 'Khoá bảng điểm',
  UNLOCK: 'Mở khoá bảng điểm',
  REOPEN: 'Mở lại bảng điểm',
  APPROVE: 'Duyệt bảng điểm',
  PUBLISH: 'Công bố bảng điểm',
  UPDATE_COMPONENTS: 'Cập nhật cấu hình thành phần',
  UPDATE_COMPONENT_SCORE: 'Cập nhật điểm thành phần',
  CLEAR_COMPONENT_SCORE: 'Xoá điểm thành phần',
  UPDATE_FINAL_SCORE: 'Cập nhật điểm tổng kết',
};

const AdminGradebooks = () => {
  const toast = useToast();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [semesterFilter, setSemesterFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(1);
  const [target, setTarget] = useState(null);
  const [acting, setActing] = useState(false);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [auditOpen, setAuditOpen] = useState(false);
  const [auditLogs, setAuditLogs] = useState([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditGradebook, setAuditGradebook] = useState(null);

  const load = async () => {
    try {
      const [progressRes, semesterRes] = await Promise.all([
        gradebookApi.getProgress(),
        semesterApi.getAll().catch(() => ({ data: [] })),
      ]);
      setItems(progressRes.data || []);
      setSemesters(semesterRes.data || []);
    } catch (err) {
      toast.error('Không tải được dữ liệu bảng điểm', getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return items.filter((item) => {
      if (semesterFilter !== 'ALL' && String(item.semesterId) !== semesterFilter) return false;
      if (statusFilter !== 'ALL' && item.status !== statusFilter) return false;
      if (!keyword) return true;
      return [item.sectionCode, item.courseCode, item.courseName, item.semesterName, item.lecturerName]
        .filter(Boolean).some((value) => value.toLowerCase().includes(keyword));
    });
  }, [items, search, semesterFilter, statusFilter]);

  const pageItems = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const totals = useMemo(() => ({
    total: items.length,
    open: items.filter((i) => i.status === 'OPEN').length,
    published: items.filter((i) => i.status === 'PUBLISHED').length,
    incomplete: items.reduce((sum, i) => sum + Number(i.incompleteStudents || 0), 0),
  }), [items]);

  const openDetail = async (item) => {
    setDetailLoading(true);
    setDetail({ summary: item, data: null });
    try {
      const res = await gradebookApi.getSectionDetail(item.sectionId);
      setDetail({ summary: item, data: res.data });
    } catch (err) {
      toast.error('Không tải được chi tiết bảng điểm', getErrorMessage(err));
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const openAudit = async (item) => {
    setAuditGradebook(item);
    setAuditOpen(true);
    setAuditLoading(true);
    setAuditLogs([]);
    try {
      const res = await gradebookApi.getAuditLogs(item.gradebookId);
      setAuditLogs(res.data || []);
    } catch (err) {
      toast.error('Không tải được nhật ký thay đổi', getErrorMessage(err));
    } finally {
      setAuditLoading(false);
    }
  };

  const runAction = async () => {
    if (!target) return;
    setActing(true);
    try {
      const res = await gradebookApi[target.type](target.item.gradebookId);
      const updated = res.data;
      setItems((prev) => prev.map((item) => (item.gradebookId === updated.gradebookId ? updated : item)));
      toast.success(`${ACTIONS[target.type].label} thành công`, `Lớp ${updated.sectionCode} đã được cập nhật.`);
      setTarget(null);
    } catch (err) {
      toast.error(`${ACTIONS[target.type].label} thất bại`, getErrorMessage(err));
    } finally {
      setActing(false);
    }
  };

  const actionButtons = (item) => {
    const buttons = [];
    if (item.status === 'OPEN') {
      buttons.push(
        <button key="lock" className="btn btn-secondary btn-sm" onClick={() => setTarget({ type: 'lock', item })}>
          <LockIcon size={14} /> Khoá
        </button>,
      );
    }
    if (item.status === 'LOCKED' || item.status === 'APPROVED' || item.status === 'PUBLISHED') {
      buttons.push(
        <button key="unlock" className="btn btn-secondary btn-sm" onClick={() => setTarget({ type: 'unlock', item })}>
          <RefreshIcon size={14} /> Mở khoá
        </button>,
      );
    }
    if (item.status === 'LOCKED') {
      buttons.push(
        <button key="approve" className="btn btn-primary btn-sm" onClick={() => setTarget({ type: 'approve', item })}>
          <CheckCircleIcon size={14} /> Duyệt
        </button>,
      );
    }
    if (item.status === 'APPROVED') {
      buttons.push(
        <button key="publish" className="btn btn-primary btn-sm" onClick={() => setTarget({ type: 'publish', item })}>
          <AwardIcon size={14} /> Công bố
        </button>,
      );
    }
    return buttons;
  };

  return (
    <>
      <PageHeader
        title="Quản lý bảng điểm"
        subtitle="Theo dõi tiến độ nhập điểm, khoá, duyệt và công bố bảng điểm theo từng lớp học phần."
        actions={(
          <>
            <button className="btn btn-secondary" onClick={load}><RefreshIcon /> Làm mới</button>
            <Link to="/admin/gradebook-config" className="btn btn-primary"><ClipboardIcon /> Cấu hình thành phần điểm</Link>
          </>
        )}
      />

      <div className="stats-grid">
        <StatCard label="Tổng bảng điểm" value={totals.total} icon={<ClipboardIcon />} tone="blue" />
        <StatCard label="Đang mở nhập điểm" value={totals.open} icon={<UsersIcon />} tone="green" />
        <StatCard label="Đã công bố" value={totals.published} icon={<AwardIcon />} tone="indigo" />
        <StatCard label="Sinh viên chưa đủ điểm" value={totals.incomplete} icon={<FileTextIcon />} tone="amber" />
      </div>

      <div className="toolbar">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(1); }} placeholder="Tìm lớp, môn học, giảng viên..." />
        <select className="select" style={{ width: 210 }} value={semesterFilter} onChange={(e) => { setSemesterFilter(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả học kỳ</option>
          {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName}</option>)}
        </select>
        <select className="select" style={{ width: 180 }} value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="OPEN">Đang mở</option>
          <option value="LOCKED">Đã khoá</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="PUBLISHED">Đã công bố</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} bảng điểm</span>
      </div>

      <div className="card">
        {loading ? <LoadingBlock /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<AwardIcon />}
            title="Chưa có bảng điểm"
            description="Chưa có lớp học phần nào phù hợp với bộ lọc hiện tại."
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Lớp học phần</th>
                    <th>Học kỳ</th>
                    <th>Giảng viên</th>
                    <th>Tiến độ nhập điểm</th>
                    <th>Hình thức</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => {
                    const done = Number(item.completedStudents || 0);
                    const total = Number(item.totalStudents || 0);
                    const percent = total ? Math.round((done / total) * 100) : 0;
                    return (
                      <tr key={item.gradebookId}>
                        <td>
                          <div className="cell-strong">{item.sectionCode}</div>
                          <div className="cell-muted">{item.courseCode} · {item.courseName}</div>
                        </td>
                        <td className="cell-muted">{item.semesterName || '—'}</td>
                        <td>{item.lecturerName || <span className="badge badge-amber">Chưa phân công</span>}</td>
                        <td>
                          <div className="progress-cell">
                            <div className="progress-cell-head">
                              <strong>{done}/{total}</strong>
                              <span className={item.incompleteStudents > 0 ? 'text-warning' : 'text-success'}>
                                {item.incompleteStudents > 0 ? `Thiếu ${item.incompleteStudents}` : 'Đủ điểm'}
                              </span>
                            </div>
                            <div className="progress-track">
                              <div
                                className={`progress-fill${percent === 100 ? ' complete' : ''}`}
                                style={{ width: `${percent}%` }}
                              />
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="chip">{item.gradingMode === 'COMPONENT' ? `${item.componentCount} thành phần` : 'Điểm cuối kỳ'}</span>
                        </td>
                        <td><StatusBadge status={item.status} /></td>
                        <td>
                          <div className="row-actions">
                            <button className="btn btn-secondary btn-sm" onClick={() => openDetail(item)}><EyeIcon size={14} /> Xem</button>
                            <button className="btn btn-secondary btn-sm" onClick={() => openAudit(item)}><FileTextIcon size={14} /> Nhật ký</button>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => navigate(`/admin/gradebook-config?section=${item.sectionId}`)}
                            >
                              <ClipboardIcon size={14} /> Cấu hình
                            </button>
                            {actionButtons(item)}
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

      <ConfirmDialog
        open={!!target}
        title={target ? ACTIONS[target.type].title : ''}
        message={target ? (
          `Bạn có chắc muốn ${ACTIONS[target.type].label.toLowerCase()} cho lớp ${target.item.sectionCode}? `
          + 'Thao tác này sẽ được ghi nhận vào nhật ký và không thể hoàn tác nếu chưa mở lại.'
        ) : ''}
        confirmLabel={target ? ACTIONS[target.type].label : 'Xác nhận'}
        tone={target ? ACTIONS[target.type].tone : 'primary'}
        loading={acting}
        onConfirm={runAction}
        onCancel={() => setTarget(null)}
      />

      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail ? `Bảng điểm lớp ${detail.summary.sectionCode}` : 'Bảng điểm'}
        subtitle={detail ? `${detail.summary.courseCode} · ${detail.summary.semesterName || ''}` : ''}
        size="lg"
        footer={<button className="btn btn-secondary" onClick={() => setDetail(null)}>Đóng</button>}
      >
        {detailLoading ? <LoadingBlock /> : !detail?.data ? (
          <EmptyState title="Không có dữ liệu" description="Không thể tải chi tiết bảng điểm." />
        ) : (
          <>
            {detail.data.components?.length > 0 && (
              <Alert tone="info">
                Thành phần điểm: {detail.data.components.map((c) => `${c.componentName} (${c.weight}%)`).join(' · ')}
              </Alert>
            )}
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Sinh viên</th>
                    {detail.data.components?.map((c) => <th key={c.id}>{c.componentName}</th>)}
                    <th>Điểm tổng kết</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.data.enrollments?.length ? detail.data.enrollments.map((row) => (
                    <tr key={row.enrollmentId}>
                      <td>
                        <div className="cell-strong">{row.fullName}</div>
                        <div className="cell-muted">{row.studentCode}</div>
                      </td>
                      {detail.data.components?.map((c) => (
                        <td key={c.id}>{formatScore(row.componentScores?.[c.id])}</td>
                      ))}
                      <td><strong>{formatScore(row.finalScore)}</strong></td>
                      <td>
                        <span className={`badge badge-${row.complete ? 'green' : 'amber'}`}>
                          {row.complete ? 'Đã đủ điểm' : 'Chưa đủ điểm'}
                        </span>
                      </td>
                    </tr>
                  )) : (
                    <tr><td colSpan={4} className="cell-muted">Lớp chưa có sinh viên đăng ký.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Modal>

      <Modal
        open={auditOpen}
        onClose={() => setAuditOpen(false)}
        title="Nhật ký thay đổi điểm"
        subtitle={auditGradebook ? `Lớp ${auditGradebook.sectionCode}` : ''}
        size="lg"
        footer={<button className="btn btn-secondary" onClick={() => setAuditOpen(false)}>Đóng</button>}
      >
        {auditLoading ? <LoadingBlock /> : auditLogs.length === 0 ? (
          <EmptyState icon={<FileTextIcon />} title="Chưa có nhật ký" description="Chưa có thay đổi điểm hoặc trạng thái bảng điểm nào được ghi nhận." />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Thời điểm</th>
                  <th>Người thực hiện</th>
                  <th>Hành động</th>
                  <th>Đối tượng</th>
                  <th>Trước</th>
                  <th>Sau</th>
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((log) => (
                  <tr key={log.id}>
                    <td className="cell-muted">{formatDateTime(log.createdAt)}</td>
                    <td>{log.actor}</td>
                    <td><span className="badge badge-blue">{ACTION_LABELS[log.action] || log.action}</span></td>
                    <td>
                      <div className="cell-strong">{log.studentName || '—'}</div>
                      <div className="cell-muted">{log.studentCode || ''}{log.componentName ? ` · ${log.componentName}` : ''}</div>
                    </td>
                    <td className="cell-muted">{log.oldValue ?? '—'}</td>
                    <td className="cell-muted">{log.newValue ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
    </>
  );
};

export default AdminGradebooks;
