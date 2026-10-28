import httpClient from './httpClient';

export const authApi = {
  login: (data) => httpClient.post('/api/auth/login', data),
  register: (data) => httpClient.post('/api/auth/register', data),
  forgotPassword: (data) => httpClient.post('/api/auth/forgot-password', data),
  logout: () => httpClient.post('/api/auth/logout'),
  me: () => httpClient.get('/api/me'),
  updateProfile: (data) => httpClient.put('/api/me', data),
};

export const studentApi = {
  getAll: () => httpClient.get('/api/students'),
  getById: (id) => httpClient.get(`/api/students/${id}`),
  create: (data) => httpClient.post('/api/students', data),
  update: (id, data) => httpClient.put(`/api/students/${id}`, data),
  getMyGrades: () => httpClient.get('/api/student/grades'),
};

export const lecturerApi = {
  getAll: () => httpClient.get('/api/lecturers'),
  create: (data) => httpClient.post('/api/lecturers', data),
  update: (id, data) => httpClient.put(`/api/lecturers/${id}`, data),
};

export const courseApi = {
  getAll: () => httpClient.get('/api/courses'),
  create: (data) => httpClient.post('/api/courses', data),
  update: (id, data) => httpClient.put(`/api/courses/${id}`, data),
};

export const semesterApi = {
  getAll: () => httpClient.get('/api/semesters'),
  create: (data) => httpClient.post('/api/semesters', data),
  update: (id, data) => httpClient.put(`/api/semesters/${id}`, data),
};

export const courseSectionApi = {
  getAll: () => httpClient.get('/api/course-sections'),
  getMine: () => httpClient.get('/api/course-sections/mine'),
  create: (data) => httpClient.post('/api/course-sections', data),
  update: (id, data) => httpClient.put(`/api/course-sections/${id}`, data),
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

export const reportApi = {
  getDashboard: () => httpClient.get('/api/reports/dashboard'),
};
