import httpClient from './httpClient';

export const authApi = {
  login: (data) => httpClient.post('/api/auth/login', data),
  register: (data) => httpClient.post('/api/auth/register', data),
  forgotPassword: (data) => httpClient.post('/api/auth/forgot-password', data),
  resetPassword: (data) => httpClient.post('/api/auth/reset-password', data),
  logout: () => httpClient.post('/api/auth/logout'),
  me: () => httpClient.get('/api/me'),
  updateProfile: (data) => httpClient.put('/api/me', data),
  uploadAvatar: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return httpClient.post('/api/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

export const studentApi = {
  getAll: () => httpClient.get('/api/students'),
  getById: (id) => httpClient.get(`/api/students/${id}`),
  create: (data) => httpClient.post('/api/students', data),
  update: (id, data) => httpClient.put(`/api/students/${id}`, data),
  delete: (id) => httpClient.delete(`/api/students/${id}`),
  getMyGrades: () => httpClient.get('/api/student/grades'),
};

export const lecturerApi = {
  getAll: () => httpClient.get('/api/lecturers'),
  getById: (id) => httpClient.get(`/api/lecturers/${id}`),
  create: (data) => httpClient.post('/api/lecturers', data),
  update: (id, data) => httpClient.put(`/api/lecturers/${id}`, data),
  delete: (id) => httpClient.delete(`/api/lecturers/${id}`),
};

export const courseApi = {
  getAll: () => httpClient.get('/api/courses'),
  create: (data) => httpClient.post('/api/courses', data),
  update: (id, data) => httpClient.put(`/api/courses/${id}`, data),
  delete: (id) => httpClient.delete(`/api/courses/${id}`),
};

export const semesterApi = {
  getAll: () => httpClient.get('/api/semesters'),
  create: (data) => httpClient.post('/api/semesters', data),
  update: (id, data) => httpClient.put(`/api/semesters/${id}`, data),
  delete: (id) => httpClient.delete(`/api/semesters/${id}`),
};

export const courseSectionApi = {
  getAll: () => httpClient.get('/api/course-sections'),
  getMine: () => httpClient.get('/api/course-sections/mine'),
  create: (data) => httpClient.post('/api/course-sections', data),
  update: (id, data) => httpClient.put(`/api/course-sections/${id}`, data),
  delete: (id) => httpClient.delete(`/api/course-sections/${id}`),
};

export const enrollmentApi = {
  getMyEnrollments: () => httpClient.get('/api/enrollments/me'),
  enroll: (sectionId) => httpClient.post(`/api/course-sections/${sectionId}/enroll`),
  withdraw: (enrollmentId) => httpClient.delete(`/api/enrollments/${enrollmentId}`),
};

export const gradeApi = {
  getGradesBySection: (sectionId) => httpClient.get(`/api/course-sections/${sectionId}/grades`),
  updateGrade: (enrollmentId, data) => httpClient.put(`/api/enrollments/${enrollmentId}/grade`, data),
  getMyGrades: () => httpClient.get('/api/grades/me'),
};

export const gradebookApi = {
  getAll: () => httpClient.get('/api/admin/gradebooks'),
  getProgress: () => httpClient.get('/api/admin/gradebooks/progress'),
  getById: (gradebookId) => httpClient.get(`/api/admin/gradebooks/${gradebookId}`),
  getAuditLogs: (gradebookId) => httpClient.get(`/api/admin/gradebooks/${gradebookId}/audit-logs`),
  lock: (gradebookId) => httpClient.post(`/api/admin/gradebooks/${gradebookId}/lock`),
  unlock: (gradebookId) => httpClient.post(`/api/admin/gradebooks/${gradebookId}/unlock`),
  approve: (gradebookId) => httpClient.post(`/api/admin/gradebooks/${gradebookId}/approve`),
  publish: (gradebookId) => httpClient.post(`/api/admin/gradebooks/${gradebookId}/publish`),
  getSectionDetail: (sectionId) => httpClient.get(`/api/course-sections/${sectionId}/gradebook`),
  getComponents: (sectionId) => httpClient.get(`/api/course-sections/${sectionId}/grade-components`),
  updateComponents: (sectionId, components) => httpClient.put(`/api/course-sections/${sectionId}/grade-components`, { components }),
};

export const feedbackApi = {
  getMy: () => httpClient.get('/api/feedbacks/my'),
  getAssigned: () => httpClient.get('/api/feedbacks/assigned'),
  getAll: (status, type) => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (type) params.append('type', type);
    const query = params.toString();
    return httpClient.get(`/api/feedbacks${query ? `?${query}` : ''}`);
  },
  getById: (id) => httpClient.get(`/api/feedbacks/${id}`),
  create: (data) => httpClient.post('/api/feedbacks', data),
  assign: (id, data) => httpClient.put(`/api/feedbacks/${id}/assign`, data),
  updateStatus: (id, data) => httpClient.put(`/api/feedbacks/${id}/status`, data),
  addMessage: (id, data) => httpClient.post(`/api/feedbacks/${id}/messages`, data),
};

export const reportApi = {
  getDashboard: () => httpClient.get('/api/reports/dashboard'),
};

export const notificationApi = {
  getAll: () => httpClient.get('/api/notifications'),
  getUnreadCount: () => httpClient.get('/api/notifications/unread-count'),
  markAsRead: (id) => httpClient.put(`/api/notifications/${id}/read`),
  markAllAsRead: () => httpClient.put('/api/notifications/read-all'),
  create: (data) => httpClient.post('/api/notifications', data),
  delete: (id) => httpClient.delete(`/api/notifications/${id}`),
};
