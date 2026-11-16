import React from 'react';
import {
  BotIcon, MessageSquareIcon, MinusIcon, PlusIcon, XIcon,
} from '../Icons';

const ChatHeader = ({
  view,
  onToggleView,
  onNewConversation,
  onMinimize,
  onClose,
  conversationTitle,
}) => (
  <div className="ai-chat-header">
    <div className="ai-chat-header-meta">
      <div className="ai-avatar-wrap">
        <div className="ai-avatar">
          <BotIcon size={20} />
        </div>
        <span className="ai-online-indicator" title="AI sẵn sàng hỗ trợ" />
      </div>
      <div>
        <div className="ai-chat-title">Trợ lý AI Sinh viên</div>
        <div className="ai-chat-subtitle">
          {view === 'history' ? (
            <span>Danh sách hội thoại</span>
          ) : (
            <>
              <span className="sub-dot" />
              <span>{conversationTitle ? (conversationTitle.length > 24 ? `${conversationTitle.slice(0, 22)}...` : conversationTitle) : 'Sẵn sàng hỗ trợ'}</span>
            </>
          )}
        </div>
      </div>
    </div>

    <div className="ai-chat-actions">
      <button
        type="button"
        className={`ai-action-btn ${view === 'history' ? 'active' : ''}`}
        title={view === 'history' ? 'Quay lại cuộc trò chuyện' : 'Lịch sử hội thoại'}
        onClick={onToggleView}
        aria-label="Lịch sử hội thoại"
      >
        <MessageSquareIcon size={15} />
      </button>

      <button
        type="button"
        className="ai-action-btn"
        title="Cuộc trò chuyện mới"
        onClick={onNewConversation}
        aria-label="Tạo cuộc trò chuyện mới"
      >
        <PlusIcon size={15} />
      </button>

      <button
        type="button"
        className="ai-action-btn"
        title="Thu nhỏ"
        onClick={onMinimize}
        aria-label="Thu nhỏ cửa sổ chat"
      >
        <MinusIcon size={15} />
      </button>

      <button
        type="button"
        className="ai-action-btn"
        title="Đóng"
        onClick={onClose}
        aria-label="Đóng cửa sổ chat"
      >
        <XIcon size={15} />
      </button>
    </div>
  </div>
);

export default ChatHeader;
