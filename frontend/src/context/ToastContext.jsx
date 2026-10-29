import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import { AlertCircleIcon, CheckCircleIcon, InfoIcon, XIcon } from '../components/Icons';

const ToastContext = createContext(null);

const ICONS = {
  success: <CheckCircleIcon />,
  error: <AlertCircleIcon />,
  info: <InfoIcon />,
};

const swalDefaults = {
  position: 'center',
  width: 420,
  padding: '1.4rem 1.3rem',
  customClass: {
    popup: 'swal-auth-popup',
    title: 'swal-auth-title',
    htmlContainer: 'swal-auth-text',
    confirmButton: 'swal-auth-confirm',
    cancelButton: 'swal-auth-cancel',
    icon: 'swal-auth-icon',
  },
  buttonsStyling: false,
  allowOutsideClick: false,
  allowEscapeKey: false,
  backdrop: true,
  showConfirmButton: false,
  showClass: { popup: 'swal2-show' },
  hideClass: { popup: 'swal2-hide' },
};

export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showAuthModal = useCallback((config = {}) => {
    const {
      icon = 'success',
      title = 'Thông báo',
      message = '',
      duration = 2000,
      showConfirmButton: hasConfirmButton = duration === 0,
      confirmButtonText = 'Đã hiểu',
    } = config;

    Swal.close();

    return Swal.fire({
      ...swalDefaults,
      icon,
      title,
      text: message,
      timer: duration > 0 ? duration : undefined,
      timerProgressBar: duration > 0,
      showConfirmButton: hasConfirmButton,
      confirmButtonText,
      confirmButtonColor: '#2563eb',
      width: 420,
      background: '#ffffff',
      color: '#0f172a',
      borderRadius: '18px',
      didOpen: () => {
        const popup = document.querySelector('.swal2-popup');
        if (popup) {
          popup.style.borderRadius = '16px';
          popup.style.boxShadow = '0 20px 50px rgba(15, 23, 42, 0.18)';
        }
      },
    });
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
    showAuthModal,
    hideAuthModal: () => Swal.close(),
  }), [push, showAuthModal]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <style>{`
        .swal-auth-popup {
          border-radius: 16px !important;
          box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18) !important;
          border: 1px solid rgba(148, 163, 184, 0.18) !important;
          padding: 1.2rem 1.2rem 1rem !important;
        }
        .swal-auth-title {
          font-size: 1.15rem !important;
          font-weight: 800 !important;
          color: #0f172a !important;
        }
        .swal-auth-text {
          font-size: 0.96rem !important;
          color: #475569 !important;
          line-height: 1.6 !important;
        }
        .swal-auth-icon {
          margin: 0 auto 0.65rem !important;
        }
        .swal-auth-confirm {
          border-radius: 10px !important;
          padding: 0.7rem 1.1rem !important;
          font-weight: 600 !important;
          background: #2563eb !important;
          color: #fff !important;
          border: none !important;
        }
        .swal-auth-cancel {
          border-radius: 10px !important;
          padding: 0.7rem 1.1rem !important;
          font-weight: 600 !important;
        }
      `}</style>
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
