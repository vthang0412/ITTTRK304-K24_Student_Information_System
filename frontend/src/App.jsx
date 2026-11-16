import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import ProtectedRoute from './routes/ProtectedRoute';
import Layout from './components/Layout';
import Landing from './pages/Landing';
import Login from './pages/Login';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Dashboard from './pages/Dashboard';
import Unauthorized from './pages/Unauthorized';
import NotFound from './pages/NotFound';
import Profile from './pages/Profile';
import StudentCourses from './pages/student/StudentCourses';
import StudentEnrollments from './pages/student/StudentEnrollments';
import StudentTimetable from './pages/student/StudentTimetable';
import StudentGrades from './pages/student/StudentGrades';
import LecturerSections from './pages/lecturer/LecturerSections';
import LecturerGrades from './pages/lecturer/LecturerGrades';
import LecturerNotifications from './pages/lecturer/LecturerNotifications';
import AdminStudents from './pages/admin/AdminStudents';
import AdminLecturers from './pages/admin/AdminLecturers';
import AdminCourses from './pages/admin/AdminCourses';
import AdminSemesters from './pages/admin/AdminSemesters';
import AdminSections from './pages/admin/AdminSections';
import AdminTimetable from './pages/admin/AdminTimetable';
import AdminEnrollments from './pages/admin/AdminEnrollments';
import AdminGradebooks from './pages/admin/AdminGradebooks';
import AdminGradebookConfig from './pages/admin/AdminGradebookConfig';
import AdminNotifications from './pages/admin/AdminNotifications';
import StudentNotifications from './pages/student/StudentNotifications';
import StudentFeedbacks from './pages/student/StudentFeedbacks';
import LecturerFeedbacks from './pages/lecturer/LecturerFeedbacks';
import AdminFeedbacks from './pages/admin/AdminFeedbacks';

const withLayout = (element, roles) => (
  <ProtectedRoute allowedRoles={roles}>
    <Layout>{element}</Layout>
  </ProtectedRoute>
);

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Router>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/unauthorized" element={<Unauthorized />} />

            <Route path="/dashboard" element={withLayout(<Dashboard />)} />

            <Route path="/student/profile" element={withLayout(<Profile />, ['STUDENT'])} />
            <Route path="/student/courses" element={withLayout(<StudentCourses />, ['STUDENT'])} />
            <Route path="/student/enrollments" element={withLayout(<StudentEnrollments />, ['STUDENT'])} />
            <Route path="/student/timetable" element={withLayout(<StudentTimetable />, ['STUDENT'])} />
            <Route path="/student/grades" element={withLayout(<StudentGrades />, ['STUDENT'])} />
            <Route path="/student/notifications" element={withLayout(<StudentNotifications />, ['STUDENT'])} />
            <Route path="/student/feedbacks" element={withLayout(<StudentFeedbacks />, ['STUDENT'])} />

            <Route path="/lecturer/profile" element={withLayout(<Profile />, ['LECTURER'])} />
            <Route path="/lecturer/sections" element={withLayout(<LecturerSections />, ['LECTURER'])} />
            <Route path="/lecturer/grades" element={withLayout(<LecturerGrades />, ['LECTURER'])} />
            <Route path="/lecturer/notifications" element={withLayout(<LecturerNotifications />, ['LECTURER'])} />
            <Route path="/lecturer/feedbacks" element={withLayout(<LecturerFeedbacks />, ['LECTURER'])} />

            <Route path="/admin/profile" element={withLayout(<Profile />, ['ADMIN'])} />
            <Route path="/admin/students" element={withLayout(<AdminStudents />, ['ADMIN'])} />
            <Route path="/admin/lecturers" element={withLayout(<AdminLecturers />, ['ADMIN'])} />
            <Route path="/admin/courses" element={withLayout(<AdminCourses />, ['ADMIN'])} />
            <Route path="/admin/semesters" element={withLayout(<AdminSemesters />, ['ADMIN'])} />
            <Route path="/admin/sections" element={withLayout(<AdminSections />, ['ADMIN'])} />
            <Route path="/admin/timetable" element={withLayout(<AdminTimetable />, ['ADMIN'])} />
            <Route path="/admin/enrollments" element={withLayout(<AdminEnrollments />, ['ADMIN'])} />
            <Route path="/admin/gradebooks" element={withLayout(<AdminGradebooks />, ['ADMIN'])} />
            <Route path="/admin/gradebook-config" element={withLayout(<AdminGradebookConfig />, ['ADMIN'])} />
            <Route path="/admin/notifications" element={withLayout(<AdminNotifications />, ['ADMIN'])} />
            <Route path="/admin/feedbacks" element={withLayout(<AdminFeedbacks />, ['ADMIN'])} />

            <Route path="*" element={<NotFound />} />
          </Routes>
        </Router>
      </ToastProvider>
    </AuthProvider>
  );
}

export default App;
