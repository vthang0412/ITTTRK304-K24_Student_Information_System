import axios from 'axios';

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL;
const httpClient = axios.create({
  baseURL: configuredBaseUrl !== undefined ? configuredBaseUrl : '',
  timeout: 15000,
});

const isAuthPage = (path) => (
  path === '/login' ||
  path === '/register' ||
  path === '/forgot-password' ||
  path === '/reset-password'
);

const isDisabledAccountError = (error) => {
  const response = error?.response;
  const data = response?.data;
  const rawMessage = (typeof data === 'string' ? data : data?.message) || '';
  if (response?.status !== 401) return false;
  const lc = String(rawMessage).toLowerCase();
  return (
    lc.includes('ngừng hoạt động') ||
    lc.includes('bị khóa') ||
    lc.includes('disabled') ||
    String(rawMessage) === 'Account is disabled'
  );
};

const clearSession = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
};

httpClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isCancel?.(error) || error?.code === 'ERR_CANCELED') {
      return Promise.reject(error);
    }

    const status = error?.response?.status;
    const hasToken = !!localStorage.getItem('token');
    const currentPath = window.location.pathname;

    if (status === 401 && !isDisabledAccountError(error) && hasToken && !isAuthPage(currentPath)) {
      clearSession();
      window.location.replace(`/login?reason=session_expired&redirect=${encodeURIComponent(currentPath + window.location.search)}`);
    }

    if (status === 403 && hasToken && currentPath !== '/unauthorized') {
      window.location.replace('/unauthorized');
    }

    return Promise.reject(error);
  },
);

export default httpClient;
