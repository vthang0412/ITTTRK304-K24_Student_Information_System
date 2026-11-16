import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { gradebookApi, semesterApi } from '../../api';
import { usePaginatedList } from '../../hooks/usePaginatedList';
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

const BULK_HISTORY_ACTION_LABELS = {
  OPEN: 'Mở bảng điểm hàng loạt',
  LOCK: 'Khoá bảng điểm hàng loạt',
  APPROVE: 'Duyệt bảng điểm hàng loạt',
  PUBLISH: 'Công bố bảng điểm hàng loạt',
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
  const [target, setTarget] = useState(null);
  const [acting, setActing] = useState(false);
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [bulkTarget, setBulkTarget] = useState(null);
  const [bulkActing, setBulkActing] = useState(false);
  const [bulkPreviewLoading, setBulkPreviewLoading] = useState(false);
  const [bulkResult, setBulkResult] = useState(null);
  const [bulkHistory, setBulkHistory] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [bulkHistoryLoading, setBulkHistoryLoading] = useState(false);
  const [bulkHistoryPage, setBulkHistoryPage] = useState(0);
  const [bulkHistoryFilters, setBulkHistoryFilters] = useState({
    actor: '', action: '', fromTime: '', toTime: '', course: '', section: '', semester: '', result: '',
  });
  const [appliedBulkHistoryFilters, setAppliedBulkHistoryFilters] = useState({});
  const [bulkHistoryDetail, setBulkHistoryDetail] = useState(null);
  const [bulkHistoryDetailLoading, setBulkHistoryDetailLoading] = useState(false);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [auditOpen, setAuditOpen] = useState(false);
  const [auditLogs, setAuditLogs] = useState([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditGradebook, setAuditGradebook] = useState(null);

  const loadBulkHistory = async (page = bulkHistoryPage, filters = appliedBulkHistoryFilters) => {
    setBulkHistoryLoading(true);
    try {
      const response = await gradebookApi.getBulkHistory({ ...filters, page, size: 10 });
      setBulkHistory(response.data);
    } catch (err) {
      toast.error('Không tải được lịch sử thao tác hàng loạt', getErrorMessage(err));
    } finally {
      setBulkHistoryLoading(false);
    }
  };

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
    await loadBulkHistory();
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

  const { page, setPage, pageSize, setPageSize, totalItems, pageItems } = usePaginatedList(filtered);
  const selectedCount = selectedIds.size;
  const allCurrentPageSelected = pageItems.length > 0
    && pageItems.every((item) => selectedIds.has(item.gradebookId));
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

  const togglePageSelection = () => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (allCurrentPageSelected) {
        pageItems.forEach((item) => next.delete(item.gradebookId));
      } else {
        pageItems.forEach((item) => next.add(item.gradebookId));
      }
      return next;
    });
  };

  const toggleSelection = (gradebookId) => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(gradebookId)) next.delete(gradebookId);
      else next.add(gradebookId);
      return next;
    });
  };

  const startBulkPreview = async (action) => {
    if (selectedIds.size === 0) return;
    setBulkPreviewLoading(true);
    try {
      const response = await gradebookApi.previewBulkAction([...selectedIds], action);
      setBulkTarget({ action, gradebookIds: [...selectedIds], preview: response.data });
    } catch (err) {
      toast.error('Không thể kiểm tra điều kiện bảng điểm', getErrorMessage(err));
    } finally {
      setBulkPreviewLoading(false);
    }
  };

  const runBulkAction = async () => {
    if (!bulkTarget || !bulkTarget.preview?.eligibleCount) return;
    setBulkActing(true);
    try {
      const response = await gradebookApi.bulkAction(bulkTarget.gradebookIds, bulkTarget.action);
      setBulkResult({ action: bulkTarget, ...response.data });
      setSelectedIds((previous) => {
        const next = new Set(previous);
        response.data.results
          .filter((result) => result.successful || result.skipped)
          .forEach((result) => next.delete(result.gradebookId));
        return next;
      });
      setBulkTarget(null);
      await load();
    } catch (err) {
      toast.error('Không thể xử lý hàng loạt', getErrorMessage(err));
    } finally {
      setBulkActing(false);
    }
  };

  const openBulkHistoryDetail = async (operationCode) => {
    setBulkHistoryDetailLoading(true);
    setBulkHistoryDetail(null);
    try {
      const response = await gradebookApi.getBulkHistoryDetail(operationCode);
      setBulkHistoryDetail(response.data);
    } catch (err) {
      toast.error('Không tải được chi tiết đợt thao tác', getErrorMessage(err));
    } finally {
      setBulkHistoryDetailLoading(false);
    }
  };

  const updateListFilter = (setter, value) => {
    setter(value);
    setPage(1);
    setSelectedIds(new Set());
  };

  const applyBulkHistoryFilters = async () => {
    const filters = {
      ...bulkHistoryFilters,
      fromTime: bulkHistoryFilters.fromTime ? `${bulkHistoryFilters.fromTime}T00:00:00` : '',
      toTime: bulkHistoryFilters.toTime ? `${bulkHistoryFilters.toTime}T23:59:59` : '',
    };
    setAppliedBulkHistoryFilters(filters);
    setBulkHistoryPage(0);
    await loadBulkHistory(0, filters);
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
      const hasIncomplete = Number(item.incompleteStudents || 0) > 0;
      buttons.push(
        <button
          key="approve"
          className="btn btn-primary btn-sm"
          onClick={() => setTarget({ type: 'approve', item })}
          disabled={hasIncomplete}
          title={hasIncomplete ? 'Không thể duyệt khi còn sinh viên chưa đủ điểm' : 'Duyệt bảng điểm'}
        >
          <CheckCircleIcon size={14} /> Duyệt
        </button>,
      );
    }
    if (item.status === 'APPROVED') {
      const hasIncomplete = Number(item.incompleteStudents || 0) > 0;
      buttons.push(
        <button
          key="publish"
          className="btn btn-primary btn-sm"
          onClick={() => setTarget({ type: 'publish', item })}
          disabled={hasIncomplete}
          title={hasIncomplete ? 'Không thể công bố khi còn sinh viên chưa đủ điểm' : 'Công bố bảng điểm'}
        >
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
        <SearchInput value={search} onChange={(v) => updateListFilter(setSearch, v)} placeholder="Tìm lớp, môn học, giảng viên..." />
        <select className="select" style={{ width: 210 }} value={semesterFilter} onChange={(e) => updateListFilter(setSemesterFilter, e.target.value)}>
          <option value="ALL">Tất cả học kỳ</option>
          {semesters.map((s) => <option key={s.id} value={s.id}>{s.semesterName}</option>)}
        </select>
        <select className="select" style={{ width: 180 }} value={statusFilter} onChange={(e) => updateListFilter(setStatusFilter, e.target.value)}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="OPEN">Đang mở</option>
          <option value="LOCKED">Đã khoá</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="PUBLISHED">Đã công bố</option>
        </select>
        <div className="toolbar-spacer" />
        <span className="chip">{filtered.length} bảng điểm</span>
      </div>

      <div className="toolbar" style={{ alignItems: 'center', marginBottom: 14 }}>
        <strong>{selectedCount} bảng điểm đã chọn</strong>
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={() => setSelectedIds(new Set())}
          disabled={selectedCount === 0 || bulkActing}
        >
          Bỏ chọn toàn bộ
        </button>
        <div className="toolbar-spacer" />
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => startBulkPreview('OPEN')} disabled={selectedCount === 0 || bulkActing || bulkPreviewLoading}>
          <RefreshIcon size={14} /> Mở bảng điểm
        </button>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => startBulkPreview('LOCK')} disabled={selectedCount === 0 || bulkActing || bulkPreviewLoading}>
          <LockIcon size={14} /> Khóa bảng điểm
        </button>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => startBulkPreview('APPROVE')} disabled={selectedCount === 0 || bulkActing || bulkPreviewLoading}>
          <CheckCircleIcon size={14} /> Duyệt bảng điểm
        </button>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => startBulkPreview('PUBLISH')} disabled={selectedCount === 0 || bulkActing || bulkPreviewLoading}>
          <AwardIcon size={14} /> Công bố bảng điểm
        </button>
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
                    <th>
                      <input
                        type="checkbox"
                        aria-label="Chọn tất cả bảng điểm trên trang hiện tại"
                        checked={allCurrentPageSelected}
                        onChange={togglePageSelection}
                        disabled={bulkActing}
                      />
                    </th>
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
                          <input
                            type="checkbox"
                            aria-label={`Chọn bảng điểm lớp ${item.sectionCode}, học kỳ ${item.semesterName || ''}`}
                            checked={selectedIds.has(item.gradebookId)}
                            onChange={() => toggleSelection(item.gradebookId)}
                            disabled={bulkActing}
                          />
                        </td>
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
                          <span className="chip">
                            {item.gradingMode === 'COMPONENT'
                              ? `${item.componentCount || 0} thành phần`
                              : item.gradingMode === 'FINAL'
                                ? 'Điểm cuối kỳ'
                                : item.gradingMode || '—'}
                          </span>
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
            <Pagination page={page} pageSize={pageSize} totalItems={totalItems} onPageChange={setPage} onPageSizeChange={setPageSize} />
          </>
        )}
      </div>

      <section className="card" style={{ marginTop: 20 }}>
        <div className="card-header">
          <div>
            <h2 className="card-title">Lịch sử thao tác hàng loạt</h2>
            <p className="cell-muted">Các đợt mở, khóa, duyệt và công bố bảng điểm; nhật ký cá nhân vẫn được lưu riêng.</p>
          </div>
        </div>
        <div className="toolbar" style={{ flexWrap: 'wrap' }}>
          <input className="input" aria-label="Lọc người thao tác" placeholder="ADMIN thực hiện" value={bulkHistoryFilters.actor}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, actor: e.target.value }))} />
          <select className="select" aria-label="Lọc hành động" value={bulkHistoryFilters.action}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, action: e.target.value }))}>
            <option value="">Tất cả thao tác</option>
            <option value="OPEN">Mở</option>
            <option value="LOCK">Khóa</option>
            <option value="APPROVE">Duyệt</option>
            <option value="PUBLISH">Công bố</option>
          </select>
          <input className="input" aria-label="Lọc mã hoặc tên môn học" placeholder="Mã / tên môn" value={bulkHistoryFilters.course}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, course: e.target.value }))} />
          <input className="input" aria-label="Lọc lớp học phần" placeholder="Lớp học phần" value={bulkHistoryFilters.section}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, section: e.target.value }))} />
          <input className="input" aria-label="Lọc học kỳ" placeholder="Học kỳ" value={bulkHistoryFilters.semester}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, semester: e.target.value }))} />
          <select className="select" aria-label="Lọc kết quả" value={bulkHistoryFilters.result}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, result: e.target.value }))}>
            <option value="">Mọi kết quả</option>
            <option value="SUCCESS">Thành công</option>
            <option value="FAILURE">Thất bại</option>
            <option value="SKIPPED">Bỏ qua</option>
          </select>
          <input className="input" type="date" aria-label="Từ ngày" value={bulkHistoryFilters.fromTime}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, fromTime: e.target.value }))} />
          <input className="input" type="date" aria-label="Đến ngày" value={bulkHistoryFilters.toTime}
            onChange={(e) => setBulkHistoryFilters((prev) => ({ ...prev, toTime: e.target.value }))} />
          <button type="button" className="btn btn-primary btn-sm" onClick={applyBulkHistoryFilters}>Lọc lịch sử</button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => {
            const emptyFilters = { actor: '', action: '', fromTime: '', toTime: '', course: '', section: '', semester: '', result: '' };
            setBulkHistoryFilters(emptyFilters);
            setAppliedBulkHistoryFilters({});
            setBulkHistoryPage(0);
            loadBulkHistory(0, {});
          }}>Xóa lọc</button>
        </div>
        {bulkHistoryLoading ? <LoadingBlock /> : bulkHistory.content?.length ? (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr><th>Mã đợt</th><th>ADMIN</th><th>Thời gian</th><th>Thao tác</th><th>Đã chọn</th><th>Kết quả</th><th /></tr>
                </thead>
                <tbody>
                  {bulkHistory.content.map((operation) => (
                    <tr key={operation.id}>
                      <td className="cell-muted">{operation.operationCode}</td>
                      <td>{operation.actor}</td>
                      <td>{formatDateTime(operation.createdAt)}</td>
                      <td>{BULK_HISTORY_ACTION_LABELS[operation.action] || operation.action}</td>
                      <td>{operation.selectedCount}</td>
                      <td>{operation.successCount} thành công · {operation.failureCount} thất bại · {operation.skippedCount} bỏ qua</td>
                      <td><button type="button" className="btn btn-secondary btn-sm" onClick={() => openBulkHistoryDetail(operation.operationCode)}>Chi tiết</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="toolbar">
              <span className="cell-muted">{bulkHistory.totalElements} đợt thao tác</span>
              <div className="toolbar-spacer" />
              <button type="button" className="btn btn-secondary btn-sm" disabled={bulkHistoryPage === 0 || bulkHistoryLoading}
                onClick={() => { const next = bulkHistoryPage - 1; setBulkHistoryPage(next); loadBulkHistory(next); }}>Trước</button>
              <span className="chip">{bulkHistory.totalPages ? bulkHistoryPage + 1 : 0} / {bulkHistory.totalPages}</span>
              <button type="button" className="btn btn-secondary btn-sm" disabled={bulkHistoryPage + 1 >= bulkHistory.totalPages || bulkHistoryLoading}
                onClick={() => { const next = bulkHistoryPage + 1; setBulkHistoryPage(next); loadBulkHistory(next); }}>Sau</button>
            </div>
          </>
        ) : <EmptyState title="Chưa có lịch sử hàng loạt" description="Các đợt thao tác mới sẽ xuất hiện tại đây." />}
      </section>

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
        open={!!bulkTarget}
        onClose={() => !bulkActing && setBulkTarget(null)}
        title={bulkTarget ? {
          OPEN: 'Mở bảng điểm hàng loạt',
          LOCK: 'Khóa bảng điểm hàng loạt',
          APPROVE: 'Duyệt bảng điểm hàng loạt',
          PUBLISH: 'Công bố bảng điểm hàng loạt',
        }[bulkTarget.action] : ''}
        subtitle={bulkTarget?.preview
          ? `${bulkTarget.preview.selectedCount} đã chọn · ${bulkTarget.preview.eligibleCount} đủ điều kiện · ${bulkTarget.preview.ineligibleCount} không đủ điều kiện`
          : ''}
        size="lg"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setBulkTarget(null)} disabled={bulkActing}>Quay lại</button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={runBulkAction}
              disabled={bulkActing || !bulkTarget?.preview?.eligibleCount}
            >
              {bulkActing ? 'Đang xử lý...' : `Xác nhận ${bulkTarget?.preview?.eligibleCount || 0} bảng điểm đủ điều kiện`}
            </button>
          </>
        )}
      >
        {bulkTarget?.action === 'OPEN' && (
          <Alert tone="warning">
            Mở lại bảng điểm sẽ xóa dấu thời gian duyệt/công bố theo quy trình hiện tại. Lịch học, đăng ký và điểm không bị thay đổi.
          </Alert>
        )}
        {bulkTarget?.action !== 'OPEN' && (
          <Alert tone="info">
            Hệ thống chỉ thực hiện trên các bảng điểm đủ điều kiện đã xem trước. Mỗi bảng điểm được xử lý độc lập; lịch học, đăng ký và dữ liệu điểm được giữ nguyên.
          </Alert>
        )}
        {!bulkTarget?.preview ? <LoadingBlock /> : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Kết quả kiểm tra</th>
                  <th>Môn / lớp học phần</th>
                  <th>Học kỳ</th>
                  <th>Trạng thái</th>
                  <th>Tiến độ điểm</th>
                  <th>Lý do</th>
                </tr>
              </thead>
              <tbody>
                {bulkTarget.preview.results.map((result, index) => (
                  <tr key={`${result.gradebookId}-${index}`}>
                    <td><span className={`badge ${result.eligible ? 'badge-green' : 'badge-red'}`}>{result.eligible ? 'Đủ điều kiện' : 'Không đủ điều kiện'}</span></td>
                    <td>
                      <div className="cell-strong">{result.courseCode || '—'} · {result.courseName || 'Bảng điểm'}</div>
                      <div className="cell-muted">{result.sectionCode || `ID ${result.gradebookId}`}</div>
                    </td>
                    <td>{result.semesterName || '—'}</td>
                    <td>{result.status || '—'}</td>
                    <td>{result.completedStudents ?? '—'}/{result.totalStudents ?? '—'} đủ · {result.studentsWithNoGrades ?? 0} chưa có điểm · {result.studentsWithPartialGrades ?? 0} chưa đủ điểm</td>
                    <td>{result.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>

      <Modal
        open={!!bulkResult}
        onClose={() => setBulkResult(null)}
        title={bulkResult ? `Kết quả: ${bulkResult.action.action === 'OPEN' ? 'Mở bảng điểm' : bulkResult.action.action === 'LOCK' ? 'Khóa bảng điểm' : bulkResult.action.action === 'APPROVE' ? 'Duyệt bảng điểm' : 'Công bố bảng điểm'}` : 'Kết quả xử lý'}
        subtitle={bulkResult ? `${bulkResult.selectedCount} đã chọn · ${bulkResult.successCount} thành công · ${bulkResult.failureCount} thất bại · ${bulkResult.skippedCount} bỏ qua` : ''}
        size="lg"
        footer={<button className="btn btn-secondary" onClick={() => setBulkResult(null)}>Đóng</button>}
      >
        {bulkResult && (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Kết quả</th>
                  <th>Môn / lớp học phần</th>
                  <th>Học kỳ</th>
                  <th>Trạng thái</th>
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {bulkResult.results.map((result, index) => (
                  <tr key={`${result.gradebookId}-${index}`}>
                    <td><span className={`badge ${result.successful ? 'badge-green' : result.skipped ? 'badge-amber' : 'badge-red'}`}>{result.successful ? 'Thành công' : result.skipped ? 'Bỏ qua' : 'Thất bại'}</span></td>
                    <td>
                      <div className="cell-strong">{result.courseCode || '—'} · {result.courseName || 'Bảng điểm'}</div>
                      <div className="cell-muted">{result.sectionCode || `ID ${result.gradebookId}`}</div>
                    </td>
                    <td>{result.semesterName || '—'}</td>
                    <td>{result.previousStatus || '—'}{result.successful ? ` → ${result.status}` : ''}</td>
                    <td>{result.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>

      <Modal
        open={!!bulkHistoryDetail || bulkHistoryDetailLoading}
        onClose={() => !bulkHistoryDetailLoading && setBulkHistoryDetail(null)}
        title={bulkHistoryDetail ? `Chi tiết đợt ${bulkHistoryDetail.operationCode}` : 'Chi tiết đợt thao tác'}
        subtitle={bulkHistoryDetail ? `${BULK_HISTORY_ACTION_LABELS[bulkHistoryDetail.action] || bulkHistoryDetail.action} · ${bulkHistoryDetail.actor} · ${formatDateTime(bulkHistoryDetail.createdAt)}` : ''}
        size="lg"
        footer={<button className="btn btn-secondary" onClick={() => setBulkHistoryDetail(null)} disabled={bulkHistoryDetailLoading}>Đóng</button>}
      >
        {bulkHistoryDetailLoading ? <LoadingBlock /> : bulkHistoryDetail && (
          <>
            <Alert tone="info">
              {bulkHistoryDetail.selectedCount} đã chọn · {bulkHistoryDetail.successCount} thành công · {bulkHistoryDetail.failureCount} thất bại · {bulkHistoryDetail.skippedCount} bỏ qua.
            </Alert>
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>Kết quả</th><th>Môn / lớp</th><th>Học kỳ</th><th>Trạng thái</th><th>Chi tiết</th></tr></thead>
                <tbody>
                  {bulkHistoryDetail.items.map((item, index) => (
                    <tr key={`${item.gradebookId}-${index}`}>
                      <td><span className={`badge ${item.resultStatus === 'SUCCESS' ? 'badge-green' : item.resultStatus === 'SKIPPED' ? 'badge-amber' : 'badge-red'}`}>
                        {item.resultStatus === 'SUCCESS' ? 'Thành công' : item.resultStatus === 'SKIPPED' ? 'Bỏ qua' : 'Thất bại'}
                      </span></td>
                      <td><div className="cell-strong">{item.courseCode || '—'} · {item.courseName || 'Bảng điểm'}</div><div className="cell-muted">{item.sectionCode || `ID ${item.gradebookId}`}</div></td>
                      <td>{item.semesterName || '—'}</td>
                      <td>{item.previousStatus || '—'}{item.status && item.status !== item.previousStatus ? ` → ${item.status}` : ''}</td>
                      <td>{item.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Modal>

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
                    <tr><td colSpan={(detail.data.components?.length || 0) + 3} className="cell-muted" style={{ textAlign: 'center', padding: 24 }}>Lớp chưa có sinh viên đăng ký.</td></tr>
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
