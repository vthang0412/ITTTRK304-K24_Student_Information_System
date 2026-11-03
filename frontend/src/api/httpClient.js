import axios from 'axios';

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
});

const isDisabledAccountError = (error) => {
  const response = error?.response;
  const data = response?.data;
  const rawMessage = typeof data === 'string' ? data : data?.message || '';
  return response?.status === 401 && (
    rawMessage === 'Account is disabled' ||
    rawMessage.toLowerCase().includes('disabled')
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
    if (error.response?.status === 401 && !isDisabledAccountError(error)) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      const currentPath = window.location.pathname;
      if (currentPath !== '/login' && currentPath !== '/register') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default httpClient;
