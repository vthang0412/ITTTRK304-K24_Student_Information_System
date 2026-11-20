import React, { useState } from 'react';
import {
  MessageSquareIcon, PencilIcon, PlusIcon, TrashIcon,
} from '../Icons';
import { formatDate } from '../../utils/format';

const ChatConversations = ({
  conversations,
  activeId,
  loading,
  onSelect,
  onNew,
  onDelete,
  onRename,
}) => {
  const [editingId, setEditingId] = useState(null);
  const [editingTitle, setEditingTitle] = useState('');
  const [saving, setSaving] = useState(false);

  const handleDelete = (e, id) => {
    e.stopPropagation();
    onDelete(id);
  };

  const handleStartRename = (e, item) => {
    e.stopPropagation();
    setEditingId(item.id);
    setEditingTitle(item.title || '');
  };

  const handleCancelRename = (e) => {
    e?.stopPropagation();
    setEditingId(null);
    setEditingTitle('');
  };

  const handleSubmitRename = async (e, id) => {
    e.preventDefault();
    e.stopPropagation();
    const title = editingTitle.trim();
    if (!title || title.length > 120 || saving) return;
    setSaving(true);
    try {
      await onRename(id, title);
      setEditingId(null);
      setEditingTitle('');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="ai-conversations-view">
      <div className="ai-conversations-header">
        <h4>Lịch sử trò chuyện</h4>
        <button
          type="button"
          className="ai-new-chat-btn"
          onClick={onNew}
        >
          <PlusIcon size={14} />
          <span>Hội thoại mới</span>
        </button>
      </div>

      <div className="ai-conv-list">
        {loading ? (
          <div style={{ textAlign: 'center', padding: '30px 10px', color: 'var(--muted)', fontSize: 13 }}>
            Đang tải danh sách hội thoại...
          </div>
        ) : conversations.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '36px 14px', color: 'var(--muted)' }}>
            <div style={{ marginBottom: 8, color: 'var(--blue-500)' }}>
              <MessageSquareIcon size={32} />
            </div>
            <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--text)' }}>Chưa có hội thoại nào</div>
            <div style={{ fontSize: 12, marginTop: 4, marginBottom: 16 }}>
              Bắt đầu trò chuyện với Trợ lý AI để nhận tư vấn học tập.
            </div>
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={onNew}
            >
              <PlusIcon size={14} /> Bắt đầu ngay
            </button>
          </div>
        ) : (
          conversations.map((item) => {
            const isActive = item.id === activeId;
            const isEditing = editingId === item.id;
            return (
              <div
                key={item.id}
                className={`ai-conv-item ${isActive ? 'active' : ''}`}
                onClick={() => !isEditing && onSelect(item.id)}
              >
                <div className="ai-conv-meta">
                  {isEditing ? (
                    <form
                      className="ai-conv-edit-form"
                      onSubmit={(e) => handleSubmitRename(e, item.id)}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <input
                        autoFocus
                        className="ai-conv-edit-input"
                        value={editingTitle}
                        maxLength={120}
                        onChange={(e) => setEditingTitle(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Escape') handleCancelRename(e);
                        }}
                        aria-label="Tên cuộc hội thoại"
                      />
                      <div className="ai-conv-edit-actions">
                        <button
                          type="button"
                          className="ai-conv-edit-btn"
                          onClick={handleCancelRename}
                          disabled={saving}
                        >
                          Hủy
                        </button>
                        <button
                          type="submit"
                          className="ai-conv-edit-btn primary"
                          disabled={saving || !editingTitle.trim()}
                        >
                          {saving ? '...' : 'Lưu'}
                        </button>
                      </div>
                    </form>
                  ) : (
                    <>
                      <div className="ai-conv-title" title={item.title}>
                        {item.title || 'Hội thoại học tập'}
                      </div>
                      {item.lastMessage && (
                        <div className="ai-conv-last-msg" title={item.lastMessage}>
                          {item.lastMessage}
                        </div>
                      )}
                      <div className="ai-conv-sub">
                        <span>{formatDate(item.updatedAt || item.createdAt)}</span>
                        {item.messageCount > 0 && (
                          <span>· {item.messageCount} tin nhắn</span>
                        )}
                      </div>
                    </>
                  )}
                </div>

                {!isEditing && (
                  <div className="ai-conv-actions" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className="ai-conv-rename-btn"
                      title="Đổi tên hội thoại"
                      onClick={(e) => handleStartRename(e, item)}
                      aria-label="Đổi tên cuộc hội thoại"
                    >
                      <PencilIcon size={14} />
                    </button>
                    <button
                      type="button"
                      className="ai-conv-delete-btn"
                      title="Xóa hội thoại"
                      onClick={(e) => handleDelete(e, item.id)}
                      aria-label="Xóa cuộc hội thoại"
                    >
                      <TrashIcon size={14} />
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default ChatConversations;
