import React, { useCallback, useEffect, useState } from 'react';
import { notificationApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  BellIcon, CheckCircleIcon, CheckIcon, InboxIcon, RefreshIcon,
} from '../../components/Icons';
import {
  EmptyState, LoadingBlock, PageHeader,
} from '../../components/UI';
import { formatDateTime } from '../../utils/format';

/* ─── helpers ─────────────────────────────────────────── */
const typeLabel = (type) => ({
  SYSTEM: 'Hệ thống',
  GRADE: 'Điểm số',
  ENROLLMENT: 'Đăng ký',
  ACADEMIC: 'Học thuật',
  ANNOUNCEMENT: 'Thông báo',
}[type] || 'Thông báo');

const typeColor = (type) => ({
  SYSTEM: '#6366f1',
  GRADE: '#f59e0b',
  ENROLLMENT: '#10b981',
  ACADEMIC: '#3b82f6',
  ANNOUNCEMENT: '#8b5cf6',
}[type] || '#6366f1');

/* ─── Modal chi tiết thông báo ─────────────────────────── */
const NotificationModal = ({ notification, onClose, onRead }) => {
  useEffect(() => {
    if (!notification) return undefined;
    document.body.style.overflow = 'hidden';
    if (!notification.isRead) onRead(notification.id);
    return () => { document.body.style.overflow = ''; };
  }, [notification]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!notification) return null;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 9000,
        background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 16,
      }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        role="dialog"
        aria-modal="true"
        style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 16,
          boxShadow: '0 20px 60px rgba(0,0,0,0.4)',
          maxWidth: 560, width: '100%',
          maxHeight: '85vh',
          display: 'flex', flexDirection: 'column',
          animation: 'ntfSlideUp .22s ease',
        }}
      >
        {/* Header */}
        <div style={{
          padding: '20px 24px 16px',
          borderBottom: '1px solid var(--border)',
          display: 'flex', gap: 14, alignItems: 'flex-start',
        }}>
          <div style={{
            width: 42, height: 42, borderRadius: 12,
            background: `${typeColor(notification.type)}20`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <BellIcon size={20} style={{ color: typeColor(notification.type) }} />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 700, color: 'var(--text)' }}>
              {notification.title}
            </h3>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', fontSize: 12, color: 'var(--muted)' }}>
              <span style={{
                background: `${typeColor(notification.type)}25`,
                color: typeColor(notification.type),
                borderRadius: 6, padding: '2px 8px', fontWeight: 600,
              }}>
                {typeLabel(notification.type)}
              </span>
              <span>🕐 {formatDateTime(notification.createdAt)}</span>
              {notification.senderName && (
                <span>👤 {notification.senderName}</span>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Đóng"
            style={{
              background: 'var(--surface-2)', border: 'none',
              borderRadius: 8, width: 32, height: 32,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer', color: 'var(--muted)', fontSize: 18,
              flexShrink: 0,
            }}
          >×</button>
        </div>

        {/* Body */}
        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          <p style={{ margin: 0, lineHeight: 1.8, color: 'var(--text)', whiteSpace: 'pre-wrap' }}>
            {notification.content}
          </p>
        </div>

        {/* Footer */}
        <div style={{ padding: '12px 24px 20px', borderTop: '1px solid var(--border)' }}>
          <button className="btn btn-secondary" onClick={onClose}>Đóng</button>
        </div>
      </div>
    </div>
  );
};

/* ─── Notification card ────────────────────────────────── */
const NotifCard = ({ notif, onSelect }) => (
  <button
    onClick={() => onSelect(notif)}
    style={{
      display: 'block', width: '100%', textAlign: 'left',
      background: notif.isRead ? 'var(--surface)' : 'var(--surface-2)',
      border: `1px solid ${notif.isRead ? 'var(--border)' : 'var(--primary-dim, #4f46e520)'}`,
      borderRadius: 12, padding: '16px 18px',
      cursor: 'pointer', transition: 'all .18s ease',
      position: 'relative', overflow: 'hidden',
    }}
    onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-1px)'; e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.12)'; }}
    onMouseLeave={(e) => { e.currentTarget.style.transform = ''; e.currentTarget.style.boxShadow = ''; }}
  >
    {/* Dải màu bên trái khi chưa đọc */}
    {!notif.isRead && (
      <div style={{
        position: 'absolute', left: 0, top: 0, bottom: 0, width: 4,
        background: typeColor(notif.type), borderRadius: '12px 0 0 12px',
      }} />
    )}

    <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start', paddingLeft: notif.isRead ? 0 : 8 }}>
      {/* Icon loại thông báo */}
      <div style={{
        width: 38, height: 38, borderRadius: 10, flexShrink: 0,
        background: `${typeColor(notif.type)}20`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <BellIcon size={18} style={{ color: typeColor(notif.type) }} />
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
          <p style={{
            margin: '0 0 4px', fontWeight: notif.isRead ? 500 : 700,
            fontSize: 14, color: 'var(--text)',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>
            {notif.title}
          </p>
          {!notif.isRead && (
            <div style={{
              width: 8, height: 8, borderRadius: '50%',
              background: typeColor(notif.type), flexShrink: 0, marginTop: 4,
            }} />
          )}
        </div>
        <p style={{
          margin: '0 0 8px', fontSize: 13, color: 'var(--muted)',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          maxWidth: '90%',
        }}>
          {notif.content}
        </p>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', fontSize: 11, color: 'var(--muted)' }}>
          <span style={{
            background: `${typeColor(notif.type)}18`,
            color: typeColor(notif.type),
            padding: '2px 7px', borderRadius: 5, fontWeight: 600,
          }}>
            {typeLabel(notif.type)}
          </span>
          {notif.senderName && <span>Từ: {notif.senderName}</span>}
          <span>{formatDateTime(notif.createdAt)}</span>
        </div>
      </div>
    </div>
  </button>
);

/* ─── Trang chính ──────────────────────────────────────── */
const StudentNotifications = () => {
  const toast = useToast();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const [filter, setFilter] = useState('ALL'); // ALL | UNREAD | READ
  const [markingAll, setMarkingAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await notificationApi.getAll();
      setNotifications(res.data || []);
    } catch {
      setError('Không thể tải thông báo. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleRead = async (id) => {
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      );
    } catch { /* bỏ qua – không quan trọng */ }
  };

  const handleMarkAllRead = async () => {
    setMarkingAll(true);
    try {
      await notificationApi.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      toast.success('Đã đánh dấu tất cả là đã đọc', '');
    } catch {
      toast.error('Không thể đánh dấu', 'Vui lòng thử lại sau.');
    } finally {
      setMarkingAll(false);
    }
  };

  const filtered = notifications.filter((n) => {
    if (filter === 'UNREAD') return !n.isRead;
    if (filter === 'READ') return n.isRead;
    return true;
  });

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <>
      <PageHeader
        title="Thông báo"
        subtitle="Tất cả thông báo từ nhà trường và hệ thống."
        actions={(
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-secondary btn-sm"
              onClick={load}
              title="Làm mới"
            >
              <RefreshIcon size={15} /> Làm mới
            </button>
            {unreadCount > 0 && (
              <button
                className="btn btn-primary btn-sm"
                onClick={handleMarkAllRead}
                disabled={markingAll}
              >
                <CheckIcon size={15} />
                {markingAll ? 'Đang xử lý...' : `Đánh dấu tất cả đã đọc (${unreadCount})`}
              </button>
            )}
          </div>
        )}
      />

      {/* Tab lọc */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
        {[
          { key: 'ALL', label: `Tất cả (${notifications.length})` },
          { key: 'UNREAD', label: `Chưa đọc (${unreadCount})` },
          { key: 'READ', label: `Đã đọc (${notifications.length - unreadCount})` },
        ].map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setFilter(key)}
            style={{
              padding: '7px 16px', borderRadius: 8, border: 'none',
              fontSize: 13, fontWeight: 600, cursor: 'pointer',
              transition: 'all .15s',
              background: filter === key ? 'var(--primary)' : 'var(--surface-2)',
              color: filter === key ? '#fff' : 'var(--muted)',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Nội dung */}
      {loading ? (
        <div className="card"><LoadingBlock text="Đang tải thông báo..." /></div>
      ) : error ? (
        <div className="card" style={{ padding: '32px', textAlign: 'center' }}>
          <p style={{ color: 'var(--danger, #ef4444)', marginBottom: 16 }}>{error}</p>
          <button className="btn btn-secondary" onClick={load}>Thử lại</button>
        </div>
      ) : filtered.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={<InboxIcon />}
            title={filter === 'UNREAD' ? 'Không có thông báo chưa đọc' : 'Không có thông báo'}
            description={filter === 'UNREAD' ? 'Bạn đã đọc tất cả thông báo.' : 'Chưa có thông báo nào từ nhà trường.'}
          />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {filtered.map((n) => (
            <NotifCard key={n.id} notif={n} onSelect={setSelected} />
          ))}
        </div>
      )}

      {/* Modal chi tiết */}
      <NotificationModal
        notification={selected}
        onClose={() => setSelected(null)}
        onRead={handleRead}
      />

      <style>{`
        @keyframes ntfSlideUp {
          from { opacity: 0; transform: translateY(16px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </>
  );
};

export default StudentNotifications;
