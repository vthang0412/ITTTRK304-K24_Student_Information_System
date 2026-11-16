import React from 'react';
import {
  MessageSquareIcon, PlusIcon, TrashIcon,
} from '../Icons';
import { formatDate } from '../../utils/format';

const ChatConversations = ({
  conversations,
  activeId,
  loading,
  onSelect,
  onNew,
  onDelete,
}) => {
  const handleDelete = (e, id) => {
    e.stopPropagation();
    onDelete(id);
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
            return (
              <div
                key={item.id}
                className={`ai-conv-item ${isActive ? 'active' : ''}`}
                onClick={() => onSelect(item.id)}
              >
                <div className="ai-conv-meta">
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
                </div>

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
            );
          })
        )}
      </div>
    </div>
  );
};

export default ChatConversations;
