import axios from 'axios';

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
});

/**
 * Nhận diện lỗi tài khoản bị khóa/ngừng hoạt động.
 * Backend (UnauthorizedException) trả HTTP 401 với message tiếng Việt
 * chứa "ngừng hoạt động" hoặc "bị khóa", hoặc message tiếng Anh cũ.
 */
const isDisabledAccountError = (error) => {
  const response = error?.response;
  const data = response?.data;
  const rawMessage = (typeof data === 'string' ? data : data?.message) || '';
  if (response?.status !== 401) return false;
  const lc = rawMessage.toLowerCase();
  return (
    lc.includes('ngừng hoạt động') ||
    lc.includes('bị khóa') ||
    lc.includes('disabled') ||
    rawMessage === 'Account is disabled'
  );
};

httpClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    // Nếu 401 mà KHÔNG phải do tài khoản bị khóa thì xóa session và redirect về login.
    // Trường hợp tài khoản bị khóa được xử lý riêng trong từng trang để hiển thị thông báo phù hợp.
    if (status === 401 && !isDisabledAccountError(error)) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      const currentPath = window.location.pathname;
      // Chỉ redirect nếu đang ở ngoài trang auth (tránh vòng lặp)
      if (
        currentPath !== '/login' &&
        currentPath !== '/register' &&
        currentPath !== '/forgot-password' &&
        currentPath !== '/reset-password'
      ) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default httpClient;
