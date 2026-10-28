export const getErrorMessage = (err, fallback = 'Đã có lỗi xảy ra. Vui lòng thử lại.') => {
  if (!err) return fallback;
  const data = err.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  const message = data?.message;
  if (!message) return fallback;
  const map = {
    'Invalid username or password': 'Tên đăng nhập hoặc mật khẩu không đúng.',
    'User not found': 'Không tìm thấy người dùng.',
    'Account is disabled': 'Tài khoản đã bị vô hiệu hoá.',
    'Username already exists': 'Tên đăng nhập đã tồn tại.',
    'Email already exists': 'Email đã được sử dụng.',
    'Email not found': 'Email chưa được đăng ký.',
    'Student not found': 'Không tìm thấy sinh viên.',
    'Course not found': 'Không tìm thấy môn học.',
    'Semester not found': 'Không tìm thấy học kỳ.',
    'Course section not found': 'Không tìm thấy lớp học phần.',
    'Enrollment not found': 'Không tìm thấy đăng ký học phần.',
    'Not allowed': 'Bạn không có quyền thực hiện thao tác này.',
    'Already enrolled in this section': 'Bạn đã đăng ký lớp học phần này.',
    'Course section is full': 'Lớp học phần đã đủ sĩ số.',
    'Course section is not open for registration': 'Lớp học phần chưa mở đăng ký.',
    'Gradebook is locked': 'Sổ điểm đã bị khoá.',
    'Score must be between 0 and 10': 'Điểm phải nằm trong khoảng 0 đến 10.',
    'Already withdrawn': 'Đăng ký này đã được huỷ trước đó.',
    'Student code already exists': 'Mã sinh viên đã tồn tại.',
    'An unexpected error occurred': 'Máy chủ gặp lỗi không mong muốn.',
  };
  if (map[message]) return map[message];
  if (message.startsWith('Validation')) return 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại.';
  return message;
};

export const formatDate = (value) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
};

export const formatDateTime = (value) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
};

export const toDateInput = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString().slice(0, 10);
};

export const toDateTimeInput = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export const formatScore = (value) => {
  if (value === null || value === undefined || value === '') return '—';
  const num = Number(value);
  if (Number.isNaN(num)) return '—';
  return num.toFixed(2);
};

export const gradeLabel = (score) => {
  if (score === null || score === undefined || score === '') return { label: 'Chưa có điểm', tone: 'gray' };
  const num = Number(score);
  if (Number.isNaN(num)) return { label: 'Chưa có điểm', tone: 'gray' };
  if (num >= 8.5) return { label: 'Xuất sắc', tone: 'green' };
  if (num >= 7) return { label: 'Khá', tone: 'blue' };
  if (num >= 5.5) return { label: 'Trung bình', tone: 'amber' };
  if (num >= 4) return { label: 'Yếu', tone: 'amber' };
  return { label: 'Kém', tone: 'red' };
};

export const passesCourse = (score) => {
  if (score === null || score === undefined || score === '') return false;
  return Number(score) >= 4;
};
