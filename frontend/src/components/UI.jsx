import React, { useEffect } from 'react';
import {
  AlertCircleIcon, CheckCircleIcon, ChevronLeftIcon, ChevronRightIcon,
  InfoIcon, InboxIcon, SearchIcon, XIcon,
} from './Icons';

export const PageHeader = ({ title, subtitle, actions }) => (
  <div className="page-head">
    <div>
      <h1>{title}</h1>
      {subtitle && <p>{subtitle}</p>}
    </div>
    {actions && <div className="page-head-actions">{actions}</div>}
  </div>
);

export const StatCard = ({ label, value, icon, tone = 'blue' }) => (
  <div className="stat-card">
    <div className={`stat-icon tone-${tone}`}>{icon}</div>
    <div className="stat-info">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  </div>
);

const TONES = {
  ACTIVE: 'green',
  OPEN: 'green',
  ENROLLED: 'green',
  COMPLETED: 'blue',
  PASSED: 'green',
  INACTIVE: 'gray',
  CLOSED: 'gray',
  WITHDRAWN: 'gray',
  CANCELLED: 'red',
  PENDING: 'amber',
  PLANNED: 'amber',
  UPCOMING: 'amber',
  DRAFT: 'amber',
  IN_PROGRESS: 'blue',
  RESPONDED: 'purple',
  RESOLVED: 'green',
  REJECTED: 'red',
  REGISTRATION_OPEN: 'green',
  LOCKED: 'red',
  APPROVED: 'blue',
  PUBLISHED: 'green',
  FAILED: 'red',
};

const LABELS = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  OPEN: 'Đang mở',
  CLOSED: 'Đã đóng',
  PENDING: 'Chờ tiếp nhận',
  PLANNED: 'Sắp diễn ra',
  UPCOMING: 'Sắp diễn ra',
  REGISTRATION_OPEN: 'Đang mở đăng ký',
  IN_PROGRESS: 'Đang xử lý',
  RESPONDED: 'Đã phản hồi',
  RESOLVED: 'Đã giải quyết',
  REJECTED: 'Từ chối xử lý',
  COMPLETED: 'Đã kết thúc',
  DRAFT: 'Bản nháp',
  ENROLLED: 'Đã đăng ký',
  WITHDRAWN: 'Đã huỷ',
  CANCELLED: 'Đã huỷ',
  PASSED: 'Đạt',
  FAILED: 'Không đạt',
  LOCKED: 'Đã khoá',
  APPROVED: 'Đã duyệt',
  PUBLISHED: 'Đã công bố',
  ADMIN: 'Quản trị viên',
  LECTURER: 'Giảng viên',
  STUDENT: 'Sinh viên',
};

export const statusLabel = (status) => LABELS[status] || status || '—';

export const StatusBadge = ({ status, label }) => (
  <span className={`badge badge-${TONES[status] || 'gray'}`}>{label || statusLabel(status)}</span>
);

export const Spinner = ({ large }) => <span className={`spinner${large ? ' lg' : ''}`} />;

export const LoadingBlock = ({ text = 'Đang tải dữ liệu...' }) => (
  <div className="loading-block"><Spinner /><span>{text}</span></div>
);

export const EmptyState = ({ title = 'Chưa có dữ liệu', description, action, icon }) => (
  <div className="empty-state">
    <div className="empty-icon">{icon || <InboxIcon />}</div>
    <h3>{title}</h3>
    {description && <p>{description}</p>}
    {action && <div className="mt-16">{action}</div>}
  </div>
);

const ALERT_ICONS = {
  error: <AlertCircleIcon />,
  success: <CheckCircleIcon />,
  info: <InfoIcon />,
};

export const Alert = ({ tone = 'info', children, style, className = '' }) => (
  <div className={`alert alert-${tone} ${className}`} style={style}>
    {ALERT_ICONS[tone] || <InfoIcon />}
    <div>{children}</div>
  </div>
);

export const Field = ({ label, required, error, hint, children, className = '', style }) => (
  <div className={`field ${className}`} style={style}>
    {label && <label>{label}{required && <span className="req">*</span>}</label>}
    {children}
    {error && <span className="field-error">{error}</span>}
    {!error && hint && <span className="field-hint">{hint}</span>}
  </div>
);

export const SearchInput = ({ value, onChange, placeholder = 'Tìm kiếm...' }) => (
  <div className="search-box">
    <SearchIcon />
    <input
      className="input"
      type="search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  </div>
);

export const Modal = ({ open, onClose, title, subtitle, children, footer, size = '', headerRight }) => {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div className={`modal ${size}`} role="dialog" aria-modal="true" aria-label={title || 'Modal'}>
        <div className="modal-head">
          <div className="modal-head-copy">
            {title && <h3>{title}</h3>}
            {subtitle && <div className="modal-subtitle">{subtitle}</div>}
          </div>
          <div className="modal-head-actions">
            {headerRight}
            <button className="icon-btn" onClick={onClose} aria-label="Đóng"><XIcon /></button>
          </div>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
};

export const ConfirmDialog = ({
  open, title = 'Xác nhận thao tác', message, confirmLabel = 'Xác nhận',
  cancelLabel = 'Huỷ', tone = 'danger', loading, onConfirm, onCancel,
}) => (
  <Modal
    open={open}
    onClose={loading ? () => { } : onCancel}
    title={title}
    size="sm"
    footer={(
      <>
        <button className="btn btn-secondary" onClick={onCancel} disabled={loading}>{cancelLabel}</button>
        <button className={`btn btn-${tone}`} onClick={onConfirm} disabled={loading}>
          {loading ? <Spinner /> : confirmLabel}
        </button>
      </>
    )}
  >
    <p className="muted" style={{ lineHeight: 1.65 }}>{message}</p>
  </Modal>
);

export const Pagination = ({
  page,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [10, 20, 50, 100],
}) => {
  const totalPages = Math.ceil(totalItems / pageSize);
  const currentPage = totalPages === 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
  const start = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const end = Math.min(currentPage * pageSize, totalItems);

  const pages = [];
  const from = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
  const to = Math.min(totalPages, from + 4);
  for (let i = from; i <= to; i += 1) pages.push(i);

  return (
    <div className="pagination">
      <div className="pagination-summary">
        <span className="pagination-info">Hiển thị {start}–{end} / {totalItems}</span>
        {onPageSizeChange && (
          <label className="pagination-size">
            <span>Số bản ghi/trang</span>
            <select
              className="select"
              value={pageSize}
              onChange={(event) => onPageSizeChange(Number(event.target.value))}
              aria-label="Số bản ghi mỗi trang"
            >
              {pageSizeOptions.map((size) => <option key={size} value={size}>{size}</option>)}
            </select>
          </label>
        )}
      </div>
      <nav className="pagination-controls" aria-label="Phân trang">
        <button className="page-btn" onClick={() => onPageChange(1)} disabled={currentPage <= 1} aria-label="Trang đầu">
          «
        </button>
        <button className="page-btn" onClick={() => onPageChange(currentPage - 1)} disabled={currentPage <= 1} aria-label="Trang trước">
          <ChevronLeftIcon size={15} />
        </button>
        {from > 1 && (
          <>
            <span className="muted small">…</span>
          </>
        )}
        {pages.map((p) => (
          <button key={p} className={`page-btn${p === currentPage ? ' active' : ''}`} onClick={() => onPageChange(p)} aria-current={p === currentPage ? 'page' : undefined}>{p}</button>
        ))}
        {to < totalPages && (
          <>
            <span className="muted small">…</span>
          </>
        )}
        <button className="page-btn" onClick={() => onPageChange(currentPage + 1)} disabled={currentPage >= totalPages} aria-label="Trang sau">
          <ChevronRightIcon size={15} />
        </button>
        <button className="page-btn" onClick={() => onPageChange(totalPages)} disabled={currentPage >= totalPages} aria-label="Trang cuối">
          »
        </button>
      </nav>
    </div>
  );
};
