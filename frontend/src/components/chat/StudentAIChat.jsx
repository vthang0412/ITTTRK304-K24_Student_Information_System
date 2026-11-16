import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { aiChatApi } from '../../api';
import { getErrorMessage } from '../../utils/format';
import { BotIcon } from '../Icons';
import { Modal, Spinner } from '../UI';
import ChatHeader from './ChatHeader';
import ChatMessages from './ChatMessages';
import ChatInput from './ChatInput';
import ChatConversations from './ChatConversations';
import './chat.css';

const StudentAIChat = () => {
  const { user } = useAuth();
  const toast = useToast();

  const [isOpen, setIsOpen] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [view, setView] = useState('chat'); // 'chat' | 'history'

  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);

  const [loadingConversations, setLoadingConversations] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [lastFailedText, setLastFailedText] = useState(null);
  const [deleteConversationId, setDeleteConversationId] = useState(null);
  const [deletingConversation, setDeletingConversation] = useState(false);

  // Tải danh sách các cuộc hội thoại
  const loadConversations = async (autoSelect = false) => {
    setLoadingConversations(true);
    try {
      const res = await aiChatApi.getConversations();
      const list = res.data?.data || [];
      setConversations(list);

      if (autoSelect) {
        if (list.length > 0) {
          // Chọn cuộc hội thoại mới nhất
          const latestId = list[0].id;
          setActiveConversationId(latestId);
          loadMessages(latestId);
        } else {
          // Nếu chưa có cuộc hội thoại nào, khởi tạo mới
          await handleCreateNewConversation();
        }
      }
    } catch (err) {
      // Báo lỗi nhẹ nhàng trong khung chat
      setError(getErrorMessage(err, 'Không tải được danh sách hội thoại.'));
    } finally {
      setLoadingConversations(false);
    }
  };

  // Tải tin nhắn của một cuộc hội thoại
  const loadMessages = async (conversationId) => {
    if (!conversationId) return;
    setLoadingMessages(true);
    setError(null);
    try {
      const res = await aiChatApi.getMessages(conversationId);
      setMessages(res.data?.data || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Không tải được lịch sử tin nhắn.'));
      setMessages([]);
    } finally {
      setLoadingMessages(false);
    }
  };

  // Khi bấm mở chat
  const handleOpenChat = () => {
    setIsOpen(true);
    setIsMinimized(false);
    setView('chat');

    if (!activeConversationId) {
      loadConversations(true);
    }
  };

  // Tạo cuộc hội thoại mới
  const handleCreateNewConversation = async () => {
    try {
      const res = await aiChatApi.createConversation({ title: 'Hội thoại học tập mới' });
      const newConv = res.data?.data;
      if (newConv) {
        setConversations((prev) => [newConv, ...prev]);
        setActiveConversationId(newConv.id);
        setMessages([]);
        setError(null);
        setView('chat');
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể tạo cuộc hội thoại mới.'));
    }
  };

  // Chọn một cuộc hội thoại từ danh sách
  const handleSelectConversation = (id) => {
    setActiveConversationId(id);
    setView('chat');
    loadMessages(id);
  };

  const handleConfirmDeleteConversation = async () => {
    if (deleteConversationId == null || deletingConversation) return;

    const id = deleteConversationId;
    setDeletingConversation(true);
    try {
      await aiChatApi.deleteConversation(id);
      const remaining = conversations.filter((c) => c.id !== id);
      setConversations(remaining);
      setDeleteConversationId(null);
      toast.success('Đã xóa cuộc trò chuyện', 'Cuộc trò chuyện và toàn bộ tin nhắn đã được xóa.');

      if (activeConversationId === id) {
        if (remaining.length > 0) {
          setActiveConversationId(remaining[0].id);
          loadMessages(remaining[0].id);
        } else {
          setActiveConversationId(null);
          setMessages([]);
          // Tự động tạo hội thoại mới nếu đã xóa hết
          handleCreateNewConversation();
        }
      }
    } catch (err) {
      const message = getErrorMessage(err, 'Không thể xóa cuộc trò chuyện.');
      setError(message);
      toast.error('Không thể xóa cuộc trò chuyện', message);
    } finally {
      setDeletingConversation(false);
    }
  };

  // Gửi tin nhắn đến AI
  const handleSendMessage = async (text) => {
    if (!text || !text.trim() || sending) return;

    let targetConvId = activeConversationId;

    // Nếu chưa có hội thoại nào, tạo mới trước
    if (!targetConvId) {
      try {
        const createRes = await aiChatApi.createConversation({ title: 'Hội thoại học tập mới' });
        const newConv = createRes.data?.data;
        if (!newConv) throw new Error('Không thể khởi tạo hội thoại');
        targetConvId = newConv.id;
        setActiveConversationId(newConv.id);
        setConversations((prev) => [newConv, ...prev]);
      } catch (err) {
        setError(getErrorMessage(err, 'Không thể khởi tạo hội thoại.'));
        return;
      }
    }

    // Thêm tin nhắn tạm của user vào giao diện
    const userMsg = {
      id: `temp-${Date.now()}`,
      role: 'USER',
      content: text,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setSending(true);
    setError(null);
    setLastFailedText(null);

    try {
      const res = await aiChatApi.sendMessage(targetConvId, { message: text });
      const assistantData = res.data?.data;

      if (assistantData) {
        const assistantMsg = {
          id: assistantData.messageId || `ai-${Date.now()}`,
          role: 'ASSISTANT',
          content: assistantData.content,
          sources: assistantData.sources || [],
          createdAt: assistantData.createdAt || new Date().toISOString(),
        };
        setMessages((prev) => [...prev, assistantMsg]);

        // Cập nhật lại danh sách hội thoại ngầm để đồng bộ tiêu đề tự động và tin nhắn cuối
        aiChatApi.getConversations().then((cRes) => {
          if (cRes.data?.data) {
            setConversations(cRes.data.data);
          }
        }).catch(() => {});
      }
    } catch (err) {
      const errMsg = getErrorMessage(err, 'Gửi câu hỏi thất bại. Vui lòng thử lại sau.');
      setError(errMsg);
      setLastFailedText(text);
    } finally {
      setSending(false);
    }
  };

  if (user?.role !== 'STUDENT') {
    return null;
  }

  const activeConv = conversations.find((c) => c.id === activeConversationId);

  return (
    <>
      {/* 1. Nút chat nổi (FAB) khi chưa mở hoặc đang thu nhỏ */}
      {(!isOpen || isMinimized) && (
        <div className="ai-fab-container">
          <div
            className="ai-fab-pill"
            onClick={handleOpenChat}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && handleOpenChat()}
          >
            <span className="status-dot" />
            <span>Trợ lý AI Sinh viên</span>
          </div>

          <button
            type="button"
            className="ai-fab-btn"
            onClick={handleOpenChat}
            title="Mở Trợ lý AI Sinh viên"
            aria-label="Mở Trợ lý AI Sinh viên"
          >
            <div className="ai-fab-glow" />
            <BotIcon size={28} />
            <span className="ai-fab-sparkle" title="Tính năng AI">✦</span>
          </button>
        </div>
      )}

      {/* 2. Cửa sổ chat nhỏ khi mở */}
      {isOpen && !isMinimized && (
        <div className="ai-chat-window" role="dialog" aria-label="Cửa sổ Trợ lý AI">
          <ChatHeader
            view={view}
            onToggleView={() => setView((v) => (v === 'chat' ? 'history' : 'chat'))}
            onNewConversation={handleCreateNewConversation}
            onMinimize={() => setIsMinimized(true)}
            onClose={() => setIsOpen(false)}
            conversationTitle={activeConv?.title}
          />

          <div className="ai-chat-body">
            {view === 'history' ? (
              <ChatConversations
                conversations={conversations}
                activeId={activeConversationId}
                loading={loadingConversations}
                onSelect={handleSelectConversation}
                onNew={handleCreateNewConversation}
                onDelete={setDeleteConversationId}
              />
            ) : (
              <>
                <ChatMessages
                  messages={messages}
                  sending={sending}
                  error={error}
                  onRetry={lastFailedText ? () => handleSendMessage(lastFailedText) : null}
                  onSelectSuggestion={(text) => handleSendMessage(text)}
                />

                <ChatInput
                  onSend={handleSendMessage}
                  sending={sending}
                  disabled={loadingMessages}
                />
              </>
            )}
          </div>
        </div>
      )}
      <Modal
        open={deleteConversationId !== null}
        title="Xóa cuộc trò chuyện?"
        size="sm"
        onClose={() => {
          if (!deletingConversation) setDeleteConversationId(null);
        }}
        footer={(
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setDeleteConversationId(null)}
              disabled={deletingConversation}
            >
              Hủy
            </button>
            <button
              type="button"
              className="btn btn-danger"
              onClick={handleConfirmDeleteConversation}
              disabled={deletingConversation}
            >
              {deletingConversation ? <><Spinner /> Đang xóa...</> : 'Xóa cuộc trò chuyện'}
            </button>
          </>
        )}
      >
        <p className="muted" style={{ lineHeight: 1.65 }}>
          Bạn có chắc chắn muốn xóa cuộc trò chuyện này không? Toàn bộ nội dung tin nhắn trong cuộc trò chuyện sẽ bị xóa và không thể khôi phục.
        </p>
      </Modal>
    </>
  );
};

export default StudentAIChat;
