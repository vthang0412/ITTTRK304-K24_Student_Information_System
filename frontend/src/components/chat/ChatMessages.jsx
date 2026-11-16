import React, { useEffect, useRef } from 'react';
import {
  AlertCircleIcon, BotIcon, ChevronRightIcon, SparklesIcon,
} from '../Icons';

const SUGGESTIONS = [
  'Tổng quan GPA và tín chỉ tích lũy của tôi',
  'Học kỳ này tôi đã đăng ký những môn nào?',
  'Lịch học tuần này của tôi có những lớp nào?',
  'Tư vấn cho tôi cách cải thiện kết quả học tập',
];

const formatMessageTime = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const renderFormattedText = (content) => {
  if (!content) return null;
  const lines = content.split('\n');

  return lines.map((line, idx) => {
    const trimmed = line.trim();
    const isBullet = trimmed.startsWith('* ') || trimmed.startsWith('- ') || trimmed.startsWith('• ');
    const textToParse = isBullet ? trimmed.replace(/^(\*|-|•)\s+/, '') : line;

    // Phân tách các cụm in đậm **nội dung**
    const parts = textToParse.split(/(\*\*.*?\*\*)/g);
    const parsedElements = parts.map((part, pIdx) => {
      if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
        return <strong key={pIdx}>{part.slice(2, -2)}</strong>;
      }
      return part;
    });

    if (isBullet) {
      return (
        <div key={idx} style={{ display: 'flex', gap: 6, margin: '2px 0' }}>
          <span style={{ color: '#3b82f6', fontWeight: 'bold' }}>•</span>
          <span style={{ flex: 1 }}>{parsedElements}</span>
        </div>
      );
    }

    if (!trimmed) {
      return <div key={idx} style={{ height: 6 }} />;
    }

    return (
      <div key={idx} style={{ margin: '2px 0' }}>
        {parsedElements}
      </div>
    );
  });
};

const ChatMessages = ({
  messages,
  sending,
  error,
  onRetry,
  onSelectSuggestion,
}) => {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending, error]);

  return (
    <div className="ai-messages-wrap">
      {messages.length === 0 ? (
        <div className="ai-empty-welcome">
          <div className="ai-empty-icon">
            <SparklesIcon size={26} />
          </div>
          <div className="ai-empty-title">Xin chào bạn! 👋</div>
          <div className="ai-empty-desc">
            Mình là Trợ lý AI Sinh viên của hệ thống SIS. Bạn có thể hỏi về kết quả học tập, lịch học, đăng ký môn hoặc kế hoạch học tập nhé!
          </div>

          <div className="ai-suggestions-title">Gợi ý câu hỏi nhanh:</div>
          <div className="ai-suggestions-list">
            {SUGGESTIONS.map((item, idx) => (
              <button
                key={idx}
                type="button"
                className="ai-suggest-btn"
                onClick={() => onSelectSuggestion(item)}
              >
                <span>{item}</span>
                <ChevronRightIcon size={14} />
              </button>
            ))}
          </div>
        </div>
      ) : (
        messages.map((msg, index) => {
          const isUser = msg.role === 'USER';
          const time = formatMessageTime(msg.createdAt);

          return (
            <div
              key={msg.id || index}
              className={`ai-message-row ${isUser ? 'user' : 'assistant'}`}
            >
              <div className="ai-bubble-container">
                {!isUser && (
                  <div className="ai-bot-mini-avatar" title="Trợ lý AI">
                    <BotIcon size={14} />
                  </div>
                )}
                <div>
                  <div className="ai-bubble">
                    <div className="ai-bubble-text">
                      {isUser ? msg.content : renderFormattedText(msg.content)}
                    </div>

                    {!isUser && Array.isArray(msg.sources) && msg.sources.length > 0 && (
                      <div className="ai-sources-wrap">
                        {msg.sources.map((src, sIdx) => (
                          <span key={sIdx} className="ai-source-chip">
                            📚 {src}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  {time && (
                    <div
                      className="ai-message-meta"
                      style={{ textAlign: isUser ? 'right' : 'left' }}
                    >
                      {time}
                    </div>
                  )}
                </div>
              </div>
            </div>
          );
        })
      )}

      {sending && (
        <div className="ai-message-row assistant">
          <div className="ai-bubble-container">
            <div className="ai-bot-mini-avatar">
              <BotIcon size={14} />
            </div>
            <div className="ai-bubble">
              <div className="ai-typing-indicator">
                <span className="ai-dot" />
                <span className="ai-dot" />
                <span className="ai-dot" />
                <span className="ai-typing-text">Trợ lý AI đang suy nghĩ...</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="ai-error-banner">
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <AlertCircleIcon size={16} />
            <span>{error}</span>
          </div>
          {onRetry && (
            <button
              type="button"
              className="ai-error-retry-btn"
              onClick={onRetry}
            >
              Thử lại
            </button>
          )}
        </div>
      )}

      <div ref={bottomRef} style={{ height: 1 }} />
    </div>
  );
};

export default ChatMessages;
