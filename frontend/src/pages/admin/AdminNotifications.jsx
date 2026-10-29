import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { notificationApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  BellIcon, CheckCircleIcon, InboxIcon, PlusIcon, RefreshIcon, TrashIcon,
} from '../../components/Icons';
import {
  ConfirmDialog, EmptyState, Field, LoadingBlock, Modal, PageHeader, StatCard,
} from '../../components/UI';
import { formatDateTime } from '../../utils/format';
import { getErrorMessage } from '../../utils/format';

/* ─── helpers ─────────────────────────────────────────── */
const typeOptions = [
  { value: 'ANNOUNCEMENT', label: 'Thông báo chung' },
  { value: 'ACADEMIC', label: 'Học thuật' },
  { value: 'GRADE', label: 'Điểm số' },
  { value: 'ENROLLMENT', label: 'Đăng ký học phần' },
  { value: 'SYSTEM', label: 'Hệ thống' },
];

const targetRoleOptions = [
  { value: '', label: 'Tất cả người dùng' },
  { value: 'STUDENT', label: 'Chỉ sinh viên' },
  { value: 'LECTURER', label: 'Chỉ giảng viên' },
  { value: 'ADMIN', label: 'Chỉ Admin' },
];

const typeColor = (type) => ({
  SYSTEM: '#6366f1',
  GRADE: '#f59e0b',
  ENROLLMENT: '#10b981',
  ACADEMIC: '#3b82f6',
  ANNOUNCEMENT: '#8b5cf6',
}[type] || '#6366f1');

const typeLabel = (type) => typeOptions.find((o) => o.value === type)?.label || type || '—';
const roleLabel = (role) => targetRoleOptions.find((o) => o.value === role)?.label || 'Tất cả';

const EMPTY_FORM = { title: '', content: '', type: 'ANNOUNCEMENT', targetRole: '' };

/* ─── Form gửi thông báo ───────────────────────────────── */
const SendModal = ({ open, onClose, onSent }) => {
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (open) { setForm(EMPTY_FORM); setErrors({}); }
  }, [open]);

  const validate = () => {
    const next = {};
    if (!form.title.trim()) next.title = 'Tiêu đề là bắt buộc.';
    else if (form.title.length > 200) next.title = 'Tiêu đề tối đa 200 ký tự.';
    if (!form.content.trim()) next.content = 'Nội dung là bắt buộc.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSend = async () => {
    if (!validate()) return;
    setSending(true);
    try {
      const payload = {
        title: form.title.trim(),
        content: form.content.trim(),
        type: form.type || 'ANNOUNCEMENT',
        targetRole: form.targetRole || null,
        recipientId: null,
      };
      await notificationApi.create(payload);
      toast.success('Gửi thông báo thành công', `"${payload.title}" đã được gửi đến ${roleLabel(payload.targetRole)}.`);
      onSent();
      onClose();
    } catch (err) {
      toast.error('Không thể gửi thông báo', getErrorMessage(err));
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={sending ? () => {} : onClose}
      title="Gửi thông báo mới"
      subtitle="Soạn và gửi thông báo đến người dùng trong hệ thống."
      footer={(
        <>
          <button className="btn btn-secondary" onClick={onClose} disabled={sending}>Huỷ</button>
          <button className="btn btn-primary" onClick={handleSend} disabled={sending}>
            {sending ? 'Đang gửi...' : <><BellIcon size={15} /> Gửi thông báo</>}
          </button>
        </>
      )}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Field label="Tiêu đề thông báo" required error={errors.title}>
          <input
            className={`input${errors.title ? ' invalid' : ''}`}
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder="VD: Thông báo lịch thi cuối kỳ"
            maxLength={200}
          />
        </Field>

        <Field label="Nội dung thông báo" required error={errors.content}>
          <textarea
            className={`input${errors.content ? ' invalid' : ''}`}
            value={form.content}
            onChange={(e) => setForm({ ...form, content: e.target.value })}
            placeholder="Nhập nội dung thông báo đầy đủ..."
            rows={5}
            style={{ resize: 'vertical', minHeight: 100 }}
          />
        </Field>

        <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <Field label="Loại thông báo">
            <select
              className="select"
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
            >
              {typeOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </Field>

          <Field label="Đối tượng nhận">
            <select
              className="select"
              value={form.targetRole}
              onChange={(e) => setForm({ ...form, targetRole: e.target.value })}
            >
              {targetRoleOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </Field>
        </div>

        {/* Preview */}
        <div style={{
          background: 'var(--surface-2)',
          border: '1px solid var(--border)',
          borderRadius: 10, padding: '12px 14px',
          fontSize: 13,
        }}>
          <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text)' }}>Xem trước:</div>
          <div style={{ color: 'var(--muted)' }}>
            Thông báo loại <strong style={{ color: typeColor(form.type) }}>{typeLabel(form.type)}</strong>
            {' '}sẽ được gửi đến <strong>{roleLabel(form.targetRole)}</strong>.
          </div>
        </div>
      </div>
    </Modal>
  );
};

/* ─── Trang chính ──────────────────────────────────────── */
const AdminNotifications = () => {
  const toast = useToast();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sendOpen, setSendOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await notificationApi.getAll();
      setNotifications(res.data || []);
    } catch {
      toast.error('Không tải được thông báo', 'Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load(); }, [load]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await notificationApi.delete(deleteTarget.id);
      toast.success('Đã xoá thông báo', `"${deleteTarget.title}" đã được xoá.`);
      setNotifications((prev) => prev.filter((n) => n.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch (err) {
      toast.error('Không thể xoá', getErrorMessage(err));
    } finally {
      setDeleting(false);
    }
  };

  const stats = useMemo(() => ({
    total: notifications.length,
    read: notifications.filter((n) => n.isRead).length,
    unread: notifications.filter((n) => !n.isRead).length,
  }), [notifications]);

  return (
    <>
      <PageHeader
        title="Quản lý thông báo"
        subtitle="Gửi và quản lý thông báo đến sinh viên, giảng viên trong hệ thống."
        actions={(
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-secondary btn-sm" onClick={load} title="Làm mới">
              <RefreshIcon size={15} /> Làm mới
            </button>
            <button className="btn btn-primary" onClick={() => setSendOpen(true)}>
              <PlusIcon size={16} /> Gửi thông báo
            </button>
          </div>
        )}
      />

      {/* Thống kê */}
      <div className="stats-grid">
        <StatCard label="Tổng thông báo" value={stats.total} icon={<BellIcon />} tone="blue" />
        <StatCard label="Đã đọc" value={stats.read} icon={<CheckCircleIcon />} tone="green" />
        <StatCard label="Chưa đọc" value={stats.unread} icon={<BellIcon />} tone="amber" />
      </div>

      {/* Danh sách */}
      <div className="card">
        {loading ? (
          <LoadingBlock />
        ) : notifications.length === 0 ? (
          <EmptyState
            icon={<InboxIcon />}
            title="Chưa có thông báo nào"
            description="Nhấn nút Gửi thông báo để tạo thông báo mới."
            action={<button className="btn btn-primary" onClick={() => setSendOpen(true)}><PlusIcon /> Gửi thông báo</button>}
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tiêu đề</th>
                  <th>Loại</th>
                  <th>Đối tượng</th>
                  <th>Người gửi</th>
                  <th>Thời gian</th>
                  <th>Trạng thái</th>
                  <th style={{ textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((n) => (
                  <tr key={n.id}>
                    <td className="cell-strong" style={{ maxWidth: 220 }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {n.title}
                      </div>
                    </td>
                    <td>
                      <span style={{
                        background: `${typeColor(n.type)}20`,
                        color: typeColor(n.type),
                        padding: '3px 9px', borderRadius: 6, fontSize: 12, fontWeight: 600,
                      }}>
                        {typeLabel(n.type)}
                      </span>
                    </td>
                    <td>{n.recipientName || roleLabel(n.targetRole)}</td>
                    <td className="cell-muted">{n.senderName || 'Admin'}</td>
                    <td className="cell-muted">{formatDateTime(n.createdAt)}</td>
                    <td>
                      <span style={{
                        background: n.isRead ? 'var(--badge-green-bg, #16a34a20)' : 'var(--badge-amber-bg, #d9770620)',
                        color: n.isRead ? '#16a34a' : '#d97706',
                        padding: '3px 9px', borderRadius: 6, fontSize: 12, fontWeight: 600,
                      }}>
                        {n.isRead ? 'Đã đọc' : 'Chưa đọc'}
                      </span>
                    </td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => setDeleteTarget(n)}
                        >
                          <TrashIcon size={14} /> Xoá
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal gửi thông báo */}
      <SendModal open={sendOpen} onClose={() => setSendOpen(false)} onSent={load} />

      {/* Xác nhận xoá */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Xoá thông báo"
        message={`Bạn có chắc muốn xoá thông báo "${deleteTarget?.title}"? Hành động này không thể hoàn tác.`}
        confirmLabel="Xoá"
        tone="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  );
};

export default AdminNotifications;
