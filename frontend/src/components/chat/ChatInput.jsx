import React, { useEffect, useRef, useState } from 'react';
import { SendIcon } from '../Icons';

const ChatInput = ({ onSend, sending, disabled }) => {
  const [text, setText] = useState('');
  const textareaRef = useRef(null);

  useEffect(() => {
    if (!sending && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [sending]);

  // Tự động điều chỉnh chiều cao textarea theo nội dung
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 110)}px`;
  }, [text]);

  const handleSubmit = (e) => {
    if (e) e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed || sending || disabled) return;
    onSend(trimmed);
    setText('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className="ai-chat-input-wrap">
      <form onSubmit={handleSubmit} className="ai-input-form">
        <textarea
          ref={textareaRef}
          className="ai-textarea"
          placeholder={sending ? 'Trợ lý AI đang trả lời...' : 'Hỏi về GPA, môn học hoặc nhờ giải thích/luyện tập/review code...'}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={sending || disabled}
          rows={1}
          maxLength={2000}
        />
        <button
          type="submit"
          className="ai-send-btn"
          disabled={!text.trim() || sending || disabled}
          title="Gửi câu hỏi (Enter)"
          aria-label="Gửi tin nhắn"
        >
          <SendIcon size={16} />
        </button>
      </form>
      <div className="ai-input-footer-hint">
        <span>Enter để gửi · Shift+Enter xuống dòng</span>
        <span>{text.length}/2000</span>
      </div>
    </div>
  );
};

export default ChatInput;
