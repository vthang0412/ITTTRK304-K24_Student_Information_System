import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import { AlertCircleIcon, CheckCircleIcon, InfoIcon, XIcon } from '../components/Icons';

const ToastContext = createContext(null);

const ICONS = {
  success: <CheckCircleIcon />,
  error: <AlertCircleIcon />,
  info: <InfoIcon />,
};

export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((type, title, message) => {
    const id = ++idRef.current;
    setToasts((prev) => [...prev, { id, type, title, message }]);
    window.setTimeout(() => dismiss(id), 4500);
    return id;
  }, [dismiss]);

  const api = React.useMemo(() => ({
    success: (title, message) => push('success', title, message),
    error: (title, message) => push('error', title, message),
    info: (title, message) => push('info', title, message),
  }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`} role="status">
            <div className="toast-icon">{ICONS[t.type]}</div>
            <div className="toast-body">
              <strong>{t.title}</strong>
              {t.message && <p>{t.message}</p>}
            </div>
            <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Đóng">
              <XIcon size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
};
