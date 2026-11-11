export const getErrorMessage = (err, fallback = 'Đã có lỗi xảy ra. Vui lòng thử lại.') => {
  if (!err) return fallback;
  const data = err.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  const message = data?.message;
  if (!message) return fallback;
  // Map message tiếng Anh cũ → tiếng Việt
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
    'Lecturer not found': 'Không tìm thấy giảng viên.',
    'Email is required': 'Email là bắt buộc.',
    'Full name is required': 'Họ và tên là bắt buộc.',
    'Password must be at least 6 characters': 'Mật khẩu phải có ít nhất 6 ký tự.',
    'Only inactive accounts can be deleted.': 'Chỉ có thể xoá tài khoản ở trạng thái ngừng hoạt động.',
    'Admin accounts cannot be deleted.': 'Không thể xoá tài khoản quản trị viên.',
    'Account is linked to multiple profiles and cannot be deleted automatically.': 'Tài khoản liên kết với nhiều hồ sơ, không thể tự động xoá.',
    'Account is linked to a student profile and cannot be deleted here.': 'Tài khoản liên kết với hồ sơ sinh viên, không thể xoá ở đây.',
    'Account is linked to a lecturer profile and cannot be deleted here.': 'Tài khoản liên kết với hồ sơ giảng viên, không thể xoá ở đây.',
    'Gradebook not found': 'Không tìm thấy sổ điểm.',
    'Cannot change grade components when the gradebook is not open': 'Không thể thay đổi cấu hình thành phần điểm khi sổ điểm chưa mở.',
    'Cannot change grade components after component scores have been entered': 'Không thể thay đổi cấu hình thành phần điểm sau khi đã có điểm thành phần được nhập.',
    'Component name is required': 'Tên thành phần điểm là bắt buộc.',
    'Component names must be unique': 'Tên các thành phần điểm không được trùng nhau.',
    'Component weight is required': 'Trọng số thành phần điểm là bắt buộc.',
    'Component weight must be greater than 0 and at most 100': 'Trọng số phải lớn hơn 0 và không vượt quá 100.',
    'Component max score must be greater than 0 and at most 100': 'Điểm tối đa phải lớn hơn 0 và không vượt quá 100.',
    'Total component weight must equal 100%': 'Tổng trọng số các thành phần phải bằng 100%.',
    'Only an open gradebook can be locked': 'Chỉ có thể khoá sổ điểm đang ở trạng thái mở.',
    'Gradebook is already open': 'Sổ điểm đã ở trạng thái mở.',
    'Gradebook must be locked before it can be approved': 'Sổ điểm cần được khoá trước khi duyệt.',
    'Cannot approve a gradebook with incomplete grades': 'Không thể duyệt sổ điểm khi còn sinh viên chưa đủ điểm.',
    'Gradebook must be approved before it can be published': 'Sổ điểm cần được duyệt trước khi công bố.',
    'Cannot publish a gradebook with incomplete grades': 'Không thể công bố sổ điểm khi còn sinh viên chưa đủ điểm.',
    'Gradebook is locked or not editable': 'Sổ điểm đã bị khoá hoặc không được phép chỉnh sửa.',
    'Cannot grade a withdrawn enrollment': 'Không thể nhập điểm cho sinh viên đã huỷ học phần.',
    'Component scores are required for this course section': 'Học phần này yêu cầu nhập điểm thành phần.',
    'Component score does not belong to this gradebook configuration': 'Thành phần điểm không thuộc cấu hình của sổ điểm này.',
    'You are not assigned to this course section': 'Bạn không được phân công giảng dạy lớp học phần này.',
    'Admin permission is required': 'Yêu cầu quyền quản trị viên.',
    'An unexpected error occurred': 'Máy chủ gặp lỗi không mong muốn.',
  };
  if (map[message]) return map[message];
  // Backend trả message tiếng Việt trực tiếp – trả về nguyên văn
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

export const ATTENDANCE_MIN_SCORE = 4.0;

export const gradeLabel = (score, attendanceScore = null) => {
  // Điểm liệt chuyên cần: nếu điểm chuyên cần đã nhập và dưới 4.0 điểm -> Mặc định xếp loại Kém
  if (attendanceScore !== null && attendanceScore !== undefined && attendanceScore !== '') {
    const att = Number(attendanceScore);
    if (!Number.isNaN(att) && att < ATTENDANCE_MIN_SCORE) {
      return { label: 'Kém', tone: 'red', reason: 'Chuyên cần < 4.0' };
    }
  }

  if (score === null || score === undefined || score === '') return { label: 'Chưa có điểm', tone: 'gray' };
  const num = Number(score);
  if (Number.isNaN(num)) return { label: 'Chưa có điểm', tone: 'gray' };
  if (num >= 8.5) return { label: 'Xuất sắc', tone: 'green' };
  if (num >= 7) return { label: 'Khá', tone: 'blue' };
  if (num >= 5.5) return { label: 'Trung bình', tone: 'amber' };
  if (num >= 4) return { label: 'Yếu', tone: 'amber' };
  return { label: 'Kém', tone: 'red' };
};

export const passesCourse = (score, attendanceScore = null) => {
  if (attendanceScore !== null && attendanceScore !== undefined && attendanceScore !== '') {
    const att = Number(attendanceScore);
    if (!Number.isNaN(att) && att < ATTENDANCE_MIN_SCORE) {
      return false;
    }
  }
  if (score === null || score === undefined || score === '') return false;
  return Number(score) >= 4;
};
