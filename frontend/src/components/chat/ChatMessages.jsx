import React, { useEffect, useRef, useState } from 'react';
import {
  AlertCircleIcon, BotIcon, CheckIcon, ChevronRightIcon, SparklesIcon,
} from '../Icons';

const SUGGESTIONS = [
  'Tổng quan GPA và tín chỉ tích lũy của tôi',
  'Học kỳ này tôi đã đăng ký những môn nào?',
  'Giải thích khái niệm cơ sở dữ liệu cho tôi',
  'Gợi ý bài tập luyện SQL ở mức cơ bản',
  'Review đoạn code Java này và chỉ ra lỗi',
  'Tư vấn cho tôi cách cải thiện kết quả học tập',
];

const formatMessageTime = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const formatMessageDateTime = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
};

const safeHref = (url) => {
  try {
    const parsed = new URL(url, window.location.origin);
    if (!['http:', 'https:', 'mailto:'].includes(parsed.protocol)) return null;
    if (parsed.username || parsed.password) return null;
    return parsed.href;
  } catch {
    return null;
  }
};

const renderInline = (text, keyPrefix) => {
  if (!text) return null;
  const tokens = text.split(/(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g);
  return tokens.map((token, idx) => {
    const key = `${keyPrefix}-${idx}`;
    if (token.startsWith('**') && token.endsWith('**') && token.length > 4) {
      return <strong key={key}>{token.slice(2, -2)}</strong>;
    }
    if (token.startsWith('`') && token.endsWith('`') && token.length > 2) {
      return <code key={key} className="ai-inline-code">{token.slice(1, -1)}</code>;
    }
    const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
    if (linkMatch) {
      const href = safeHref(linkMatch[2]);
      if (href) {
        return <a key={key} href={href} target="_blank" rel="noopener noreferrer">{linkMatch[1]}</a>;
      }
      return <span key={key}>{linkMatch[1]}</span>;
    }
    return <span key={key}>{token}</span>;
  });
};

const renderTable = (lines, keyPrefix) => {
  const rows = lines
    .map((line) => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim()))
    .filter((row) => !row.every((cell) => /^:?-{2,}:?$/.test(cell)));
  if (rows.length === 0) return null;
  const [header, ...body] = rows;
  return (
    <div key={keyPrefix} className="ai-md-table-wrap">
      <table className="ai-md-table">
        <thead>
          <tr>{header.map((cell, idx) => <th key={idx}>{renderInline(cell, `${keyPrefix}-h-${idx}`)}</th>)}</tr>
        </thead>
        <tbody>
          {body.map((row, rIdx) => (
            <tr key={rIdx}>
              {row.map((cell, cIdx) => <td key={cIdx}>{renderInline(cell, `${keyPrefix}-b-${rIdx}-${cIdx}`)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const renderTextPart = (text, keyPrefix) => {
  if (!text) return null;
  const lines = text.replace(/^\n+|\n+$/g, '').split('\n');
  const output = [];
  let index = 0;
  let tableBuffer = [];

  const flushTable = () => {
    if (tableBuffer.length === 0) return;
    output.push(renderTable(tableBuffer, `${keyPrefix}-table-${output.length}`));
    tableBuffer = [];
  };

  while (index < lines.length) {
    const raw = lines[index];
    const trimmed = raw.trim();

    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      tableBuffer.push(raw);
      index += 1;
      continue;
    }
    flushTable();

    if (!trimmed) {
      output.push(<div key={`${keyPrefix}-gap-${index}`} style={{ height: 6 }} />);
      index += 1;
      continue;
    }

    const heading = trimmed.match(/^(#{1,4})\s+(.*)$/);
    if (heading) {
      const Tag = `h${Math.min(heading[1].length + 2, 6)}`;
      output.push(<Tag key={`${keyPrefix}-h-${index}`} className="ai-md-heading">{renderInline(heading[2], `${keyPrefix}-h-${index}`)}</Tag>);
      index += 1;
      continue;
    }

    const bullet = trimmed.match(/^[-*•]\s+(.*)$/);
    if (bullet) {
      output.push(
        <div key={`${keyPrefix}-li-${index}`} className="ai-md-list-item">
          <span className="ai-md-bullet">•</span>
          <span>{renderInline(bullet[1], `${keyPrefix}-li-${index}`)}</span>
        </div>,
      );
      index += 1;
      continue;
    }

    const numbered = trimmed.match(/^\d+[.)]\s+(.*)$/);
    if (numbered) {
      const numberMatch = trimmed.match(/^(\d+)/);
      output.push(
        <div key={`${keyPrefix}-ol-${index}`} className="ai-md-list-item">
          <span className="ai-md-number">{numberMatch ? numberMatch[1] : '1'}.</span>
          <span>{renderInline(numbered[1], `${keyPrefix}-ol-${index}`)}</span>
        </div>,
      );
      index += 1;
      continue;
    }

    output.push(
      <div key={`${keyPrefix}-p-${index}`} className="ai-md-paragraph">
        {renderInline(trimmed, `${keyPrefix}-p-${index}`)}
      </div>,
    );
    index += 1;
  }
  flushTable();
  return output;
};

const renderFormattedText = (content, copiedKey, onCopy) => {
  if (!content) return null;
  const segments = content.split(/```([\s\S]*?)```/g);
  return segments.map((segment, idx) => {
    const key = `segment-${idx}`;
    if (idx % 2 === 1) {
      return (
        <div key={key} className="ai-code-block-wrap">
          <button
            type="button"
            className="ai-copy-code-btn"
            onClick={() => onCopy(segment, key)}
            title="Sao chép mã"
          >
            {copiedKey === key ? <><CheckIcon size={12} /> Đã copy</> : 'Sao chép'}
          </button>
          <pre className="ai-code-block"><code>{segment.replace(/^\n|\n$/g, '')}</code></pre>
        </div>
      );
    }
    return <React.Fragment key={key}>{renderTextPart(segment, key)}</React.Fragment>;
  });
};

const contextualSuggestions = (messages) => {
  const lastAssistant = [...messages].reverse().find((msg) => msg.role === 'ASSISTANT');
  const content = (lastAssistant?.content || '').toLowerCase();
  if (!lastAssistant) return SUGGESTIONS.slice(0, 4);
  if (content.includes('gpa') || content.includes('tín chỉ') || content.includes('diem')) {
    return ['Môn nào tôi chưa đạt?', 'Phân tích điểm theo từng học kỳ', 'Lập kế hoạch cải thiện GPA'];
  }
  if (content.includes('code') || content.includes('java') || content.includes('sql')) {
    return ['Giải thích thêm đoạn code này', 'Đưa ra bài tập tương tự', 'Có cách nào tối ưu hơn không?'];
  }
  if (content.includes('bài tập') || content.includes('quiz')) {
    return ['Đưa ra đáp án gợi ý', 'Tăng độ khó lên mức nâng cao', 'Giải thích từng bước'];
  }
  return ['Giải thích rõ hơn', 'Cho tôi một ví dụ cụ thể', 'Gợi ý bài tập tiếp theo'];
};

const ChatMessages = ({
  messages,
  sending,
  error,
  onRetry,
  onSelectSuggestion,
  onContinue,
  continuingMessageId,
}) => {
  const scrollRef = useRef(null);
  const bottomRef = useRef(null);
  const autoScrollRef = useRef(true);
  const [copiedKey, setCopiedKey] = useState(null);

  useEffect(() => {
    if (autoScrollRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, sending, error]);

  useEffect(() => {
    if (!copiedKey) return undefined;
    const timer = window.setTimeout(() => setCopiedKey(null), 1600);
    return () => window.clearTimeout(timer);
  }, [copiedKey]);

  const handleScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    autoScrollRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 90;
  };

  const handleCopy = async (text, key) => {
    try {
      if (!navigator.clipboard?.writeText) return;
      await navigator.clipboard.writeText(text);
      setCopiedKey(key);
    } catch {
      setCopiedKey(null);
    }
  };

  const showFollowUps = !sending && messages.length > 0 && messages[messages.length - 1]?.role === 'ASSISTANT';

  return (
    <div className="ai-messages-wrap" ref={scrollRef} onScroll={handleScroll}>
      {messages.length === 0 ? (
        <div className="ai-empty-welcome">
          <div className="ai-empty-icon">
            <SparklesIcon size={26} />
          </div>
          <div className="ai-empty-title">Xin chào bạn! 👋</div>
          <div className="ai-empty-desc">
            Mình là Trợ lý AI Sinh viên của hệ thống SIS. Bạn có thể hỏi về kết quả học tập, lịch học, đăng ký môn, hoặc nhờ mình giải thích kiến thức, tạo bài tập, quiz và review code!
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
          const dateTime = formatMessageDateTime(msg.createdAt);

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
                      {isUser ? msg.content : renderFormattedText(msg.content, `${msg.id || index}`, handleCopy)}
                    </div>

                    {!isUser && (msg.truncated || msg.canContinue) && (
                      <div className="ai-truncated-notice">
                        <span>
                          {msg.finishReason === 'LENGTH'
                            ? 'Câu trả lời đã dừng do giới hạn độ dài của mô hình.'
                            : msg.finishReason === 'BACKEND_LIMIT'
                              ? 'Câu trả lời đã đạt giới hạn độ dài an toàn của hệ thống.'
                              : 'Câu trả lời có thể chưa hoàn tất.'}
                        </span>
                        {msg.canContinue && (
                          <button
                            type="button"
                            className="ai-continue-btn"
                            disabled={continuingMessageId != null}
                            onClick={() => onContinue?.(msg)}
                            title="Viết tiếp phần còn lại"
                          >
                            {continuingMessageId === msg.id ? 'Đang tiếp tục...' : 'Tiếp tục câu trả lời'}
                          </button>
                        )}
                      </div>
                    )}
                  </div>

                  {time && (
                    <div
                      className="ai-message-meta"
                      style={{ textAlign: isUser ? 'right' : 'left' }}
                      title={dateTime}
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

      {showFollowUps && (
        <div className="ai-followups">
          {contextualSuggestions(messages).map((item, idx) => (
            <button
              key={idx}
              type="button"
              className="ai-followup-btn"
              onClick={() => onSelectSuggestion(item)}
            >
              {item}
            </button>
          ))}
        </div>
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
