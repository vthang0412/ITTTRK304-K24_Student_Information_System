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
  getRecurringSchedule: (id) => httpClient.get(`/api/course-sections/${id}/recurring-schedules`),
  checkRecurringSchedule: (id, data) => httpClient.post(`/api/course-sections/${id}/recurring-schedules/check`, data),
  saveRecurringSchedule: (id, data) => httpClient.put(`/api/course-sections/${id}/recurring-schedules`, data),
  deleteRecurringSchedule: (id) => httpClient.delete(`/api/course-sections/${id}/recurring-schedules`),
};

export const enrollmentApi = {
  getMyEnrollments: () => httpClient.get('/api/enrollments/me'),
  getPendingEnrollments: () => httpClient.get('/api/enrollments/pending'),
  getAdminEnrollments: (status) => httpClient.get('/api/enrollments/admin', {
    params: { status: status || undefined },
  }),
  enroll: (sectionId) => httpClient.post(`/api/course-sections/${sectionId}/enroll`),
  approve: (enrollmentId) => httpClient.post(`/api/enrollments/${enrollmentId}/approve`),
  reject: (enrollmentId, reason) => httpClient.post(`/api/enrollments/${enrollmentId}/reject`, { reason }),
  approveBulk: (enrollmentIds) => httpClient.post('/api/enrollments/bulk-approve', { enrollmentIds }),
  rejectBulk: (enrollmentIds, reason, reasonsByEnrollmentId) =>
    httpClient.post('/api/enrollments/bulk-reject', { enrollmentIds, reason, reasonsByEnrollmentId }),
  previewBulk: (enrollmentIds, action) =>
    httpClient.post('/api/enrollments/bulk-preview', { enrollmentIds, action }),
  getBulkHistory: (params) => httpClient.get('/api/enrollments/bulk-history', { params }),
  getBulkHistoryDetail: (operationId) => httpClient.get(`/api/enrollments/bulk-history/${operationId}`),
  withdraw: (enrollmentId) => httpClient.delete(`/api/enrollments/${enrollmentId}`),
};

export const timetableApi = {
  getWeek: ({ semesterId, weekStart, page = 1, size = 10 }) => httpClient.get('/api/timetable', {
    params: { semesterId: semesterId || undefined, weekStart, page, size },
  }),
  getAdminWeek: ({ semesterId, sectionId, lecturerId, classroom, weekStart, page = 1, size = 50 }) =>
    httpClient.get('/api/timetable/admin', {
      params: {
        semesterId: semesterId || undefined,
        sectionId: sectionId || undefined,
        lecturerId: lecturerId || undefined,
        classroom: classroom || undefined,
        weekStart,
        page,
        size,
      },
    }),
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
  bulkAction: (gradebookIds, action) => httpClient.post('/api/admin/gradebooks/bulk-actions', { gradebookIds, action }),
  previewBulkAction: (gradebookIds, action) => httpClient.post('/api/admin/gradebooks/bulk-actions/preview', { gradebookIds, action }),
  getBulkHistory: (filters) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params.append(key, value);
    });
    return httpClient.get(`/api/admin/gradebooks/bulk-actions/history?${params.toString()}`);
  },
  getBulkHistoryDetail: (operationCode) => httpClient.get(`/api/admin/gradebooks/bulk-actions/history/${encodeURIComponent(operationCode)}`),
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
