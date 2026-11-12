import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { usePaginatedList } from '../../hooks/usePaginatedList';
import { enrollmentApi, semesterApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CheckCircleIcon, ClipboardIcon, ClockIcon, UserIcon } from '../../components/Icons';
import {
  EmptyState, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatusBadge,
} from '../../components/UI';
import { formatDateTime, getErrorMessage } from '../../utils/format';

const REJECTION_REASONS = [
  ['LEFT', 'Sinh viên xin thôi học môn'],
  ['RESERVED', 'Sinh viên bảo lưu/nghỉ học'],
  ['ACADEMIC', 'Chưa đáp ứng điều kiện đăng ký'],
  ['WRONG_COURSE', 'Sinh viên chọn sai lớp học phần'],
  ['TUITION', 'Chưa xác nhận thanh toán học phí'],
  ['OTHER', 'Lý do khác'],
];

const AdminEnrollments = () => {
  const toast = useToast();
  const [enrollments, setEnrollments] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({
    semesterId: '',
    courseId: '',
    sectionId: '',
    status: 'PENDING',
    fromTime: '',
    toTime: '',
  });
  const [processingId, setProcessingId] = useState(null);
  const [bulkProcessing, setBulkProcessing] = useState(false);
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [rejectDialog, setRejectDialog] = useState(null);
  const [approvalPreview, setApprovalPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [commonReason, setCommonReason] = useState('');
  const [commonOtherReason, setCommonOtherReason] = useState('');
  const [individualReasons, setIndividualReasons] = useState({});
  const [useIndividualReasons, setUseIndividualReasons] = useState(false);
  const [historyFilters, setHistoryFilters] = useState({
    admin: '',
    semesterId: '',
    student: '',
    course: '',
    section: '',
    action: '',
    result: '',
    fromTime: '',
    toTime: '',
  });
  const [historyPage, setHistoryPage] = useState(1);
  const [historyRows, setHistoryRows] = useState([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyDetail, setHistoryDetail] = useState(null);
  const [historyDetailLoading, setHistoryDetailLoading] = useState(false);

  const load = async (status = filters.status) => {
    try {
      const res = await enrollmentApi.getAdminEnrollments(status);
      const items = res.data || [];
      const pendingIds = new Set(items
        .filter((enrollment) => enrollment.status === 'PENDING')
        .map((enrollment) => enrollment.id));
      setEnrollments(items);
      setSelectedIds((current) => new Set([...current].filter((id) => pendingIds.has(id))));
    } catch (err) {
      toast.error('Không tải được đơn đăng ký', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void Promise.resolve().then(load); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    void Promise.resolve().then(async () => {
      try {
        const { data } = await semesterApi.getAll();
        setSemesters(data || []);
      } catch (err) {
        toast.error('Không tải được danh sách học kỳ', getErrorMessage(err, 'Vui lòng thử lại sau.'));
      }
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return enrollments.filter((e) => {
      const values = [
        e.student?.studentCode,
        e.student?.fullName,
        e.student?.email,
        e.student?.user?.email,
        e.student?.className,
        e.courseSection?.sectionCode,
        e.courseSection?.course?.courseName,
        e.courseSection?.course?.courseCode,
        e.courseSection?.semester?.semesterName,
        e.courseSection?.semester?.semesterCode,
        e.status,
      ].filter(Boolean);
      const matchesSearch = !keyword
        || values.some((value) => String(value).toLowerCase().includes(keyword));
      const matchesSemester = !filters.semesterId
        || String(e.courseSection?.semester?.id) === filters.semesterId;
      const matchesCourse = !filters.courseId
        || String(e.courseSection?.course?.id) === filters.courseId;
      const matchesSection = !filters.sectionId
        || String(e.courseSection?.id) === filters.sectionId;
      const matchesStatus = !filters.status || e.status === filters.status;
      const registeredAt = String(e.registeredAt || '').replace(' ', 'T').slice(0, 16);
      const matchesFrom = !filters.fromTime || registeredAt >= filters.fromTime;
      const matchesTo = !filters.toTime || registeredAt <= filters.toTime;
      return matchesSearch && matchesSemester && matchesCourse && matchesSection
        && matchesStatus && matchesFrom && matchesTo;
    });
  }, [enrollments, search, filters]);

  const { page, setPage, pageSize, setPageSize, totalItems, pageItems } = usePaginatedList(filtered);
  const selectedEnrollments = useMemo(
    () => enrollments.filter((enrollment) => enrollment.status === 'PENDING' && selectedIds.has(enrollment.id)),
    [enrollments, selectedIds],
  );
  const selectablePageItems = pageItems.filter((enrollment) => enrollment.status === 'PENDING');
  const allPageSelected = pageItems.length > 0
    && selectablePageItems.length > 0
    && selectablePageItems.every((enrollment) => selectedIds.has(enrollment.id));
  const semesterOptions = useMemo(() => {
    const items = new Map();
    semesters.forEach((semester) => {
      if (semester?.id != null) items.set(String(semester.id), semester.semesterName || semester.semesterCode);
    });
    enrollments.forEach(({ courseSection }) => {
      const semester = courseSection?.semester;
      if (semester?.id != null) items.set(String(semester.id), semester.semesterName || semester.semesterCode);
    });
    return [...items].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [enrollments, semesters]);
  const courseOptions = useMemo(() => {
    const items = new Map();
    enrollments.forEach(({ courseSection }) => {
      const course = courseSection?.course;
      if (course?.id != null) items.set(String(course.id), `${course.courseCode || ''} ${course.courseName || ''}`.trim());
    });
    return [...items].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [enrollments]);
  const sectionOptions = useMemo(() => {
    const items = new Map();
    enrollments.forEach(({ courseSection }) => {
      if (courseSection?.id != null) {
        items.set(String(courseSection.id), `${courseSection.sectionCode || ''} · ${courseSection.course?.courseName || ''}`.trim());
      }
    });
    return [...items].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  }, [enrollments]);

  const clearSelectionForFilterChange = (nextFilters) => {
    setFilters(nextFilters);
    if (selectedIds.size > 0) {
      toast.info('Đã xóa lựa chọn cũ', 'Thay đổi bộ lọc đã bỏ lựa chọn trước đó để tránh xử lý nhầm.');
    }
    setSelectedIds(new Set());
    setApprovalPreview(null);
    setPage(1);
  };

  const loadHistory = async (targetPage = historyPage, currentFilters = historyFilters) => {
    setHistoryLoading(true);
    try {
      const params = Object.fromEntries(
        Object.entries(currentFilters).filter(([, value]) => value !== ''),
      );
      const { data } = await enrollmentApi.getBulkHistory({
        ...params,
        page: targetPage - 1,
        size: 10,
      });
      setHistoryRows(data.content || []);
      setHistoryTotal(data.totalElements || 0);
    } catch (err) {
      toast.error('Không tải được lịch sử hàng loạt', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => { void Promise.resolve().then(() => loadHistory(1)); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const togglePageSelection = (checked) => {
    setSelectedIds((current) => {
      const updated = new Set(current);
      pageItems.forEach(({ id, status }) => {
        if (checked && status === 'PENDING') updated.add(id);
        else updated.delete(id);
      });
      return updated;
    });
  };

  const toggleEnrollmentSelection = (id, checked) => {
    setSelectedIds((current) => {
      const updated = new Set(current);
      if (checked) updated.add(id);
      else updated.delete(id);
      return updated;
    });
  };

  const handleApprove = async (enrollment) => {
    const result = await Swal.fire({
      title: 'Duyệt đơn đăng ký?',
      html: `Duyệt <b>${escapeHtml(enrollment.student?.fullName || 'sinh viên')}</b> vào lớp <b>${escapeHtml(enrollment.courseSection?.sectionCode || '')}</b> — ${escapeHtml(enrollment.courseSection?.course?.courseName || '')}?`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Duyệt',
      cancelButtonText: 'Hủy',
      reverseButtons: true,
    });
    if (!result.isConfirmed) return;

    setProcessingId(enrollment.id);
    try {
      await enrollmentApi.approve(enrollment.id);
      toast.success('Đã duyệt đơn', `Sinh viên đã được ghi danh vào lớp ${enrollment.courseSection?.sectionCode}.`);
      await load();
    } catch (err) {
      toast.error('Duyệt thất bại', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setProcessingId(null);
    }
  };

  const openRejectDialog = async (targets, isBulk = false) => {
    if (targets.length === 0 || bulkProcessing || processingId !== null) return;
    setCommonReason('');
    setCommonOtherReason('');
    setIndividualReasons({});
    setUseIndividualReasons(false);
    if (isBulk) setPreviewLoading(true);
    try {
      const preview = isBulk
        ? (await enrollmentApi.previewBulk(targets.map((enrollment) => enrollment.id), 'REJECT')).data
        : null;
      setRejectDialog({ enrollments: targets, isBulk, preview });
    } catch (err) {
      toast.error('Không thể kiểm tra lựa chọn', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setPreviewLoading(false);
    }
  };

  const resolveReason = (reasonType, otherText) => {
    if (reasonType === 'OTHER') return otherText.trim();
    return REJECTION_REASONS.find(([value]) => value === reasonType)?.[1] || '';
  };

  const handleSubmitReject = async () => {
    if (!rejectDialog || rejectDialog.enrollments.length === 0) return;
    const ids = rejectDialog.enrollments.map((enrollment) => enrollment.id);
    const reason = resolveReason(commonReason, commonOtherReason);
    const reasonsByEnrollmentId = {};

    for (const enrollment of rejectDialog.enrollments) {
      const individual = individualReasons[enrollment.id];
      const selectedIndividualReason = useIndividualReasons && individual?.reason;
      const finalReason = selectedIndividualReason
        ? resolveReason(individual.reason, individual.other || '')
        : reason;
      if (!finalReason) {
        toast.error('Thiếu lý do từ chối', 'Hãy chọn lý do và nhập nội dung nếu chọn “Lý do khác”.');
        return;
      }
      if (finalReason.length > 500) {
        toast.error('Lý do quá dài', 'Lý do từ chối không được vượt quá 500 ký tự.');
        return;
      }
      if (selectedIndividualReason || !reason) {
        reasonsByEnrollmentId[enrollment.id] = finalReason;
      }
    }

    if (!rejectDialog.isBulk) {
      const enrollment = rejectDialog.enrollments[0];
      setProcessingId(enrollment.id);
      try {
        await enrollmentApi.reject(enrollment.id, reasonsByEnrollmentId[enrollment.id] || reason);
        setRejectDialog(null);
        toast.success('Đã từ chối đơn', `Đã gửi thông báo tới sinh viên ${enrollment.student?.fullName || ''}.`);
        await load();
      } catch (err) {
        toast.error('Từ chối thất bại', getErrorMessage(err, 'Vui lòng thử lại sau.'));
      } finally {
        setProcessingId(null);
      }
      return;
    }

    setBulkProcessing(true);
    try {
      const { data } = await enrollmentApi.rejectBulk(
        ids,
        reason || undefined,
        Object.keys(reasonsByEnrollmentId).length ? reasonsByEnrollmentId : undefined,
      );
      setSelectedIds((current) => new Set([...current].filter((id) =>
        !data.results.some((result) => result.enrollmentId === id && result.success)
      )));
      setRejectDialog(null);
      await load();
      setHistoryPage(1);
      await loadHistory(1);
      await showBulkResults(data, 'REJECT', rejectDialog.enrollments);
    } catch (err) {
      toast.error('Không thể từ chối hàng loạt', getErrorMessage(err, 'Vui lòng thử lại sau.'));
      await load();
    } finally {
      setBulkProcessing(false);
    }
  };

  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[character]));

  const showBulkResults = async (data, action, originalEnrollments) => {
    const originalById = new Map(originalEnrollments.map((enrollment) => [enrollment.id, enrollment]));
    const details = (data.results || []).map((result) => {
      const enrollment = originalById.get(result.enrollmentId);
      const studentCode = result.studentCode || enrollment?.student?.studentCode || '';
      const studentName = result.studentName || enrollment?.student?.fullName || `Yêu cầu #${result.enrollmentId}`;
      const course = result.courseName || enrollment?.courseSection?.course?.courseName || '';
      const section = result.sectionCode || enrollment?.courseSection?.sectionCode || '';
      const resultLabel = result.resultStatus === 'SKIPPED' ? 'Bỏ qua'
        : result.success ? 'Thành công' : 'Thất bại';
      const color = result.success ? '#15803d' : result.resultStatus === 'SKIPPED' ? '#a16207' : '#b91c1c';
      const scheduleNote = action === 'APPROVE' && result.success && result.scheduleConfigured === false
        ? ' · Lớp chưa được thiết lập lịch học'
        : '';
      const rejectionReason = action === 'REJECT' && result.success && result.rejectionReason
        ? ` · Lý do: ${escapeHtml(result.rejectionReason)}`
        : '';
      return `<li><strong>${escapeHtml(studentCode)} ${escapeHtml(studentName)}</strong> · ${escapeHtml(course)} · ${escapeHtml(section)} — <span style="color:${color}">${resultLabel}</span>${result.message && !result.success ? `: ${escapeHtml(result.message)}` : ''}${rejectionReason}${scheduleNote}</li>`;
    }).join('');

    await Swal.fire({
      title: 'Kết quả xử lý hàng loạt',
      icon: data.failureCount === 0 && data.skippedCount === 0
        ? 'success' : data.successCount === 0 ? 'error' : 'info',
      html: `<p>Đã chọn: <strong>${data.requestedCount}</strong> · Thành công: <strong>${data.successCount}</strong> · Thất bại: <strong>${data.failureCount}</strong> · Bỏ qua: <strong>${data.skippedCount || 0}</strong></p><ul style="text-align:left;max-height:45vh;overflow:auto;padding-left:22px">${details}</ul>`,
      confirmButtonText: 'Đóng',
      width: 760,
    });
  };

  const handleSelectAllFiltered = async () => {
    const selectableFiltered = filtered.filter((enrollment) => enrollment.status === 'PENDING');
    if (selectableFiltered.length === 0) return;
    const confirmation = await Swal.fire({
      title: 'Chọn tất cả kết quả đang lọc?',
      text: `Thao tác sẽ thay thế lựa chọn hiện tại bằng toàn bộ ${selectableFiltered.length} yêu cầu chờ duyệt khớp bộ lọc, bao gồm cả các trang chưa mở.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: `Chọn ${selectableFiltered.length} yêu cầu`,
      cancelButtonText: 'Hủy',
      reverseButtons: true,
    });
    if (!confirmation.isConfirmed) return;
    setSelectedIds(new Set(selectableFiltered.map((enrollment) => enrollment.id)));
    setApprovalPreview(null);
  };

  const openApprovalPreview = async () => {
    const ids = selectedEnrollments.map((enrollment) => enrollment.id);
    if (ids.length === 0 || bulkProcessing || previewLoading) return;
    setPreviewLoading(true);
    try {
      const { data } = await enrollmentApi.previewBulk(ids, 'APPROVE');
      setApprovalPreview(data);
    } catch (err) {
      toast.error('Không thể kiểm tra điều kiện duyệt', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleBulkApprove = async () => {
    const ids = selectedEnrollments.map((enrollment) => enrollment.id);
    if (ids.length === 0 || bulkProcessing || !approvalPreview) return;
    const confirmation = await Swal.fire({
      title: 'Xác nhận duyệt các yêu cầu đã chọn?',
      text: `Tổng chọn: ${approvalPreview.requestedCount}; đủ điều kiện: ${approvalPreview.eligibleCount}; không đủ điều kiện: ${approvalPreview.ineligibleCount}; bỏ qua: ${approvalPreview.skippedCount}. Backend sẽ kiểm tra lại từng yêu cầu và lưu kết quả riêng.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Xác nhận duyệt',
      cancelButtonText: 'Quay lại lựa chọn',
      reverseButtons: true,
    });
    if (!confirmation.isConfirmed) return;

    setBulkProcessing(true);
    try {
      const { data } = await enrollmentApi.approveBulk(ids);
      setSelectedIds((current) => new Set([...current].filter((id) =>
        !data.results.some((result) => result.enrollmentId === id && result.success)
      )));
      setApprovalPreview(null);
      await load();
      setHistoryPage(1);
      await loadHistory(1);
      await showBulkResults(data, 'APPROVE', selectedEnrollments);
    } catch (err) {
      toast.error('Không thể xử lý hàng loạt', getErrorMessage(err, 'Vui lòng thử lại sau.'));
      await load();
    } finally {
      setBulkProcessing(false);
    }
  };

  const openHistoryDetail = async (operationId) => {
    setHistoryDetailLoading(true);
    try {
      const { data } = await enrollmentApi.getBulkHistoryDetail(operationId);
      setHistoryDetail(data);
    } catch (err) {
      toast.error('Không tải được chi tiết lịch sử', getErrorMessage(err, 'Vui lòng thử lại sau.'));
    } finally {
      setHistoryDetailLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Duyệt đăng ký học phần"
        subtitle="Xem xét và phê duyệt các đơn đăng ký học phần đang chờ xử lý."
        actions={(
          <div className="page-head-actions">
            <span className="chip"><ClockIcon size={15} /> {enrollments.filter((item) => item.status === 'PENDING').length} đơn chờ duyệt</span>
          </div>
        )}
      />

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon tone-amber"><ClockIcon /></div>
          <div className="stat-info"><small>Đơn chờ duyệt</small><strong>{enrollments.filter((item) => item.status === 'PENDING').length}</strong></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon tone-blue"><ClipboardIcon /></div>
          <div className="stat-info"><small>Kết quả bộ lọc</small><strong>{filtered.length}</strong></div>
        </div>
        <div className="stat-card">
          <div className="stat-icon tone-green"><CheckCircleIcon /></div>
          <div className="stat-info">
            <small>Đủ điều kiện duyệt</small>
            <strong>{approvalPreview?.eligibleCount ?? '—'}</strong>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon tone-amber"><ClockIcon /></div>
          <div className="stat-info">
            <small>Không đủ điều kiện / bỏ qua</small>
            <strong>{approvalPreview ? approvalPreview.ineligibleCount + approvalPreview.skippedCount : '—'}</strong>
          </div>
        </div>
      </div>

      <div className="toolbar">
        <SearchInput
          value={search}
          onChange={(value) => {
            setSearch(value);
            if (selectedIds.size > 0) {
              toast.info('Đã xóa lựa chọn cũ', 'Thay đổi tìm kiếm đã bỏ lựa chọn trước đó để tránh xử lý nhầm.');
            }
            setSelectedIds(new Set());
            setApprovalPreview(null);
            setPage(1);
          }}
          placeholder="Mã SV, họ tên, email, môn, lớp, học kỳ..."
        />
      </div>
      <div className="toolbar" style={{ flexWrap: 'wrap' }}>
        <select
          className="select"
          value={filters.semesterId}
          onChange={(event) => clearSelectionForFilterChange({ ...filters, semesterId: event.target.value })}
          aria-label="Lọc theo học kỳ"
        >
          <option value="">Tất cả học kỳ</option>
          {semesterOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
        </select>
        <select
          className="select"
          value={filters.courseId}
          onChange={(event) => clearSelectionForFilterChange({ ...filters, courseId: event.target.value })}
          aria-label="Lọc theo môn học"
        >
          <option value="">Tất cả môn học</option>
          {courseOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
        </select>
        <select
          className="select"
          value={filters.sectionId}
          onChange={(event) => clearSelectionForFilterChange({ ...filters, sectionId: event.target.value })}
          aria-label="Lọc theo lớp học phần"
        >
          <option value="">Tất cả lớp học phần</option>
          {sectionOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
        </select>
        <select
          className="select"
          value={filters.status}
          onChange={(event) => {
            const nextStatus = event.target.value;
            clearSelectionForFilterChange({ ...filters, status: nextStatus });
            void load(nextStatus);
          }}
          aria-label="Lọc theo trạng thái đăng ký"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="PENDING">Chờ duyệt</option>
          <option value="ENROLLED">Đã duyệt</option>
          <option value="REJECTED">Đã từ chối</option>
          <option value="WITHDRAWN">Đã rút</option>
        </select>
        <label className="field" style={{ minWidth: 200 }}>
          <span>Đăng ký từ</span>
          <input
            type="datetime-local"
            className="input"
            value={filters.fromTime}
            onChange={(event) => clearSelectionForFilterChange({ ...filters, fromTime: event.target.value })}
          />
        </label>
        <label className="field" style={{ minWidth: 200 }}>
          <span>Đăng ký đến</span>
          <input
            type="datetime-local"
            className="input"
            value={filters.toTime}
            onChange={(event) => clearSelectionForFilterChange({ ...filters, toTime: event.target.value })}
          />
        </label>
      </div>
      <div className="toolbar" style={{ flexWrap: 'wrap' }}>
        <span className="chip">{filtered.length} kết quả khớp bộ lọc</span>
        {filtered.length > 0 && (
          <button
            className="btn btn-secondary"
            onClick={handleSelectAllFiltered}
            disabled={bulkProcessing || processingId !== null}
          >
            Chọn tất cả kết quả lọc ({filtered.length})
          </button>
        )}
        {selectedIds.size > 0 && (
          <span className="chip">{selectedIds.size} yêu cầu đã chọn</span>
        )}
        <div className="toolbar-spacer" />
        {selectedIds.size > 0 && (
          <>
            <button
              className="btn btn-secondary"
              onClick={() => setSelectedIds(new Set())}
              disabled={bulkProcessing || processingId !== null}
            >
              Bỏ chọn
            </button>
            <button
              className="btn btn-primary"
              onClick={openApprovalPreview}
              disabled={bulkProcessing || processingId !== null || previewLoading}
            >
              {previewLoading ? 'Đang kiểm tra...' : 'Kiểm tra & duyệt đã chọn'}
            </button>
            <button
              className="btn btn-danger"
              onClick={() => openRejectDialog(selectedEnrollments, true)}
              disabled={bulkProcessing || processingId !== null}
            >
              Từ chối đã chọn
            </button>
          </>
        )}
        <button className="btn btn-secondary" onClick={load} disabled={bulkProcessing || processingId !== null}>Làm mới</button>
      </div>

      <div className="card">
        {loading ? <LoadingBlock text="Đang tải đơn đăng ký..." /> : pageItems.length === 0 ? (
          <EmptyState
            icon={<CheckCircleIcon />}
            title={enrollments.length === 0 ? 'Không có đơn chờ duyệt' : 'Không tìm thấy đơn phù hợp'}
            description={enrollments.length === 0
              ? 'Tất cả đơn đăng ký học phần đã được xử lý.'
              : 'Thử thay đổi từ khoá tìm kiếm.'}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>
                      <input
                        type="checkbox"
                        checked={allPageSelected}
                        onChange={(event) => togglePageSelection(event.target.checked)}
                        disabled={bulkProcessing || processingId !== null || selectablePageItems.length === 0}
                        aria-label="Chọn tất cả yêu cầu trong trang hiện tại"
                      />
                    </th>
                    <th>Sinh viên</th>
                    <th>Lớp / Ngành</th>
                    <th>Học phần</th>
                    <th>Mã lớp</th>
                    <th>Lịch học</th>
                    <th>Tín chỉ</th>
                    <th>Ngày gửi</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((e) => (
                    <tr key={e.id}>
                      <td>
                        <input
                          type="checkbox"
                          checked={selectedIds.has(e.id)}
                          onChange={(event) => toggleEnrollmentSelection(e.id, event.target.checked)}
                          disabled={e.status !== 'PENDING' || bulkProcessing || processingId !== null}
                          aria-label={`Chọn yêu cầu của ${e.student?.fullName || e.student?.studentCode || e.id}`}
                        />
                      </td>
                      <td>
                        <div className="cell-strong">{e.student?.fullName || '—'}</div>
                        <div className="cell-muted">{e.student?.studentCode || '—'}</div>
                        <div className="cell-muted">{e.student?.email || '—'}</div>
                      </td>
                      <td className="cell-muted">
                        <div className="flex" style={{ gap: 6 }}>
                          <UserIcon size={14} />
                          {e.student?.className || e.student?.major || '—'}
                        </div>
                      </td>
                      <td>
                        <div className="cell-strong">{e.courseSection?.course?.courseName || '—'}</div>
                        <div className="cell-muted">{e.courseSection?.course?.courseCode || '—'}</div>
                        <div className="cell-muted">{e.courseSection?.semester?.semesterName || '—'}</div>
                      </td>
                      <td>{e.courseSection?.sectionCode || '—'}</td>
                      <td className="cell-muted">{e.courseSection?.schedule || '—'}</td>
                      <td>{e.courseSection?.course?.credits ?? '—'}</td>
                      <td className="cell-muted">{formatDateTime(e.registeredAt)}</td>
                      <td>
                        <div className="row-actions" style={{ justifyContent: 'flex-end' }}>
                          <StatusBadge
                            status={e.status || 'PENDING'}
                            label={{
                              PENDING: 'Chờ duyệt',
                              ENROLLED: 'Đã duyệt',
                              REJECTED: 'Đã từ chối',
                              WITHDRAWN: 'Đã rút',
                            }[e.status || 'PENDING']}
                          />
                          {e.status === 'REJECTED' && (
                            <span className="cell-muted" title={e.rejectionReason || ''}>
                              {formatDateTime(e.rejectedAt)} · {e.rejectionReason || 'Không có lý do'}
                            </span>
                          )}
                          {e.status === 'ENROLLED' && e.approvedAt && (
                            <span className="cell-muted">Duyệt lúc {formatDateTime(e.approvedAt)}</span>
                          )}
                          {e.status === 'PENDING' && (
                            <>
                              <button
                                className="btn btn-primary btn-sm"
                                disabled={processingId === e.id || bulkProcessing}
                                onClick={() => handleApprove(e)}
                              >
                                Duyệt
                              </button>
                              <button
                                className="btn btn-danger btn-sm"
                                disabled={processingId === e.id || bulkProcessing}
                                onClick={() => openRejectDialog([e])}
                              >
                                Từ chối
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} pageSize={pageSize} totalItems={totalItems} onPageChange={setPage} onPageSizeChange={setPageSize} />
          </>
        )}
      </div>

      <section className="card" style={{ marginTop: 24 }}>
        <div className="page-head" style={{ padding: 20 }}>
          <div>
            <h2 style={{ margin: 0 }}>Lịch sử duyệt/từ chối hàng loạt</h2>
            <p className="cell-muted">Nhật ký riêng từng đợt; lịch sử duyệt hoặc từ chối cá nhân vẫn được lưu trong Enrollment.</p>
          </div>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => loadHistory(historyPage)}
            disabled={historyLoading}
          >
            Làm mới lịch sử
          </button>
        </div>
        <div className="toolbar" style={{ flexWrap: 'wrap', padding: '0 20px 16px' }}>
          <input
            className="input"
            value={historyFilters.admin}
            onChange={(event) => setHistoryFilters({ ...historyFilters, admin: event.target.value })}
            placeholder="ADMIN"
            aria-label="Lọc lịch sử theo người thực hiện"
          />
          <select
            className="select"
            value={historyFilters.semesterId}
            onChange={(event) => setHistoryFilters({ ...historyFilters, semesterId: event.target.value })}
            aria-label="Lọc lịch sử theo học kỳ"
          >
            <option value="">Tất cả học kỳ</option>
            {semesterOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
          </select>
          <input
            className="input"
            value={historyFilters.student}
            onChange={(event) => setHistoryFilters({ ...historyFilters, student: event.target.value })}
            placeholder="Mã / tên / email SV"
          />
          <input
            className="input"
            value={historyFilters.course}
            onChange={(event) => setHistoryFilters({ ...historyFilters, course: event.target.value })}
            placeholder="Mã / tên môn"
          />
          <input
            className="input"
            value={historyFilters.section}
            onChange={(event) => setHistoryFilters({ ...historyFilters, section: event.target.value })}
            placeholder="Lớp học phần"
          />
          <select
            className="select"
            value={historyFilters.action}
            onChange={(event) => setHistoryFilters({ ...historyFilters, action: event.target.value })}
          >
            <option value="">Mọi thao tác</option>
            <option value="APPROVE">Duyệt</option>
            <option value="REJECT">Từ chối</option>
          </select>
          <select
            className="select"
            value={historyFilters.result}
            onChange={(event) => setHistoryFilters({ ...historyFilters, result: event.target.value })}
          >
            <option value="">Mọi kết quả</option>
            <option value="SUCCESS">Thành công</option>
            <option value="FAILED">Thất bại</option>
            <option value="SKIPPED">Bỏ qua</option>
          </select>
          <label className="field" style={{ minWidth: 200 }}>
            <span>Từ thời điểm</span>
            <input
              type="datetime-local"
              className="input"
              value={historyFilters.fromTime}
              onChange={(event) => setHistoryFilters({ ...historyFilters, fromTime: event.target.value })}
            />
          </label>
          <label className="field" style={{ minWidth: 200 }}>
            <span>Đến thời điểm</span>
            <input
              type="datetime-local"
              className="input"
              value={historyFilters.toTime}
              onChange={(event) => setHistoryFilters({ ...historyFilters, toTime: event.target.value })}
            />
          </label>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setHistoryPage(1);
              loadHistory(1, historyFilters);
            }}
            disabled={historyLoading}
          >
            Lọc lịch sử
          </button>
        </div>
        {historyLoading ? <LoadingBlock text="Đang tải lịch sử..." /> : historyRows.length === 0 ? (
          <EmptyState title="Chưa có đợt xử lý hàng loạt" description="Các thao tác riêng từng sinh viên không xuất hiện trong nhật ký batch." />
        ) : (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã đợt</th>
                    <th>Thời gian / ADMIN</th>
                    <th>Học kỳ</th>
                    <th>Thao tác</th>
                    <th>Đã chọn</th>
                    <th>Thành công</th>
                    <th>Thất bại</th>
                    <th>Bỏ qua</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {historyRows.map((row) => (
                    <tr key={row.id}>
                      <td className="cell-muted">{row.batchCode}</td>
                      <td>{formatDateTime(row.createdAt)}<div className="cell-muted">{row.adminUsername}</div></td>
                      <td>{row.semesterName || '—'}</td>
                      <td>{row.action === 'APPROVE' ? 'Duyệt' : 'Từ chối'}</td>
                      <td>{row.selectedCount}</td>
                      <td>{row.successCount}</td>
                      <td>{row.failureCount}</td>
                      <td>{row.skippedCount}</td>
                      <td>
                        <button className="btn btn-secondary btn-sm" onClick={() => openHistoryDetail(row.id)}>
                          Chi tiết
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={historyPage}
              pageSize={10}
              totalItems={historyTotal}
              onPageChange={(nextPage) => {
                setHistoryPage(nextPage);
                loadHistory(nextPage);
              }}
            />
          </>
        )}
      </section>

      <Modal
        open={approvalPreview !== null}
        onClose={() => {
          if (!bulkProcessing) setApprovalPreview(null);
        }}
        title="Kiểm tra điều kiện duyệt"
        subtitle={`Đã chọn ${approvalPreview?.requestedCount || 0} yêu cầu · Đủ điều kiện ${approvalPreview?.eligibleCount || 0} · Không đủ ${approvalPreview?.ineligibleCount || 0} · Bỏ qua ${approvalPreview?.skippedCount || 0}`}
        size="xl"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setApprovalPreview(null)} disabled={bulkProcessing}>
              Quay lại lựa chọn
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleBulkApprove}
              disabled={bulkProcessing || approvalPreview?.eligibleCount === 0}
            >
              {bulkProcessing ? 'Đang xử lý...' : `Xác nhận xử lý lựa chọn (${approvalPreview?.requestedCount || 0})`}
            </button>
          </>
        )}
      >
        {approvalPreview && (
          <div className="stack" style={{ gap: 12 }}>
            <p className="cell-muted">
              Kiểm tra chỉ mang tính thời điểm. Backend sẽ kiểm tra lại trạng thái, sức chứa, điều kiện học tập, tín chỉ và lịch trùng trước khi ghi nhận từng yêu cầu. Yêu cầu không đủ điều kiện vẫn được ghi vào báo cáo với lý do.
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Sinh viên</th><th>Môn / lớp</th><th>Học kỳ</th><th>Trạng thái kiểm tra</th><th>Kết quả</th>
                  </tr>
                </thead>
                <tbody>
                  {approvalPreview.results.map((item, index) => (
                    <tr key={`${item.enrollmentId}-${index}`}>
                      <td>{item.studentCode || '—'}<div className="cell-muted">{item.studentName || `#${item.enrollmentId}`}</div></td>
                      <td>{item.courseCode || '—'} · {item.courseName || '—'}<div className="cell-muted">{item.sectionCode || '—'}</div></td>
                      <td>{item.semesterName || '—'}</td>
                      <td><StatusBadge status={item.eligible ? 'ACTIVE' : item.resultStatus === 'SKIPPED' ? 'INACTIVE' : 'REJECTED'} label={item.eligible ? 'Đủ điều kiện' : item.resultStatus === 'SKIPPED' ? 'Bỏ qua' : 'Không đủ điều kiện'} /></td>
                      <td>{item.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={rejectDialog !== null}
        onClose={() => {
          if (!bulkProcessing && processingId === null) setRejectDialog(null);
        }}
        title={rejectDialog?.isBulk ? 'Từ chối các yêu cầu đã chọn' : 'Từ chối yêu cầu đăng ký'}
        subtitle={`Đã chọn ${rejectDialog?.enrollments.length || 0} yêu cầu. Chỉ các yêu cầu còn chờ duyệt mới được xử lý.`}
        size="lg"
        footer={(
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setRejectDialog(null)}
              disabled={bulkProcessing || processingId !== null}
            >
              Hủy
            </button>
            <button
              type="button"
              className="btn btn-danger"
              onClick={handleSubmitReject}
              disabled={bulkProcessing || processingId !== null || !rejectDialog?.enrollments.length
                || (rejectDialog?.isBulk && rejectDialog.preview?.eligibleCount === 0)}
            >
              {bulkProcessing || processingId !== null
                ? 'Đang xử lý...'
                : rejectDialog?.isBulk ? 'Xác nhận từ chối' : 'Từ chối'}
            </button>
          </>
        )}
      >
        {rejectDialog && (
          <div className="stack" style={{ gap: 16 }}>
            <p className="cell-muted" style={{ margin: 0 }}>
              Lý do được lưu trong lịch sử xử lý. Lý do học phí chỉ được ghi nhận khi ADMIN chủ động xác nhận.
            </p>
            {rejectDialog.isBulk && (
              <div className="chip">
                Đủ điều kiện từ chối: {rejectDialog.preview?.eligibleCount || 0}
                {' · '}Không đủ: {rejectDialog.preview?.ineligibleCount || 0}
                {' · '}Bỏ qua: {rejectDialog.preview?.skippedCount || 0}
              </div>
            )}
            <label className="field">
              <span>Lý do chung</span>
              <select className="select" value={commonReason} onChange={(event) => setCommonReason(event.target.value)}>
                <option value="">Chọn lý do chung</option>
                {REJECTION_REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            {commonReason === 'OTHER' && (
              <label className="field">
                <span>Nội dung lý do chung</span>
                <textarea
                  className="input"
                  rows={2}
                  maxLength={500}
                  value={commonOtherReason}
                  onChange={(event) => setCommonOtherReason(event.target.value)}
                  placeholder="Nhập lý do cụ thể"
                />
              </label>
            )}
            {rejectDialog.isBulk && (
              <label className="flex" style={{ gap: 8 }}>
                <input
                  type="checkbox"
                  checked={useIndividualReasons}
                  onChange={(event) => setUseIndividualReasons(event.target.checked)}
                />
                Chọn lý do riêng cho từng sinh viên
              </label>
            )}
            <div className="stack" style={{ gap: 10 }}>
              {rejectDialog.enrollments.map((enrollment) => {
                const individual = individualReasons[enrollment.id] || { reason: '', other: '' };
                return (
                  <div key={enrollment.id} className="card" style={{ padding: 14 }}>
                    <div className="cell-strong">
                      {enrollment.student?.studentCode || '—'} · {enrollment.student?.fullName || 'Sinh viên'}
                    </div>
                    <div className="cell-muted">
                      {enrollment.courseSection?.sectionCode || '—'} · {enrollment.courseSection?.course?.courseName || '—'}
                      {' · '}{enrollment.courseSection?.semester?.semesterName || '—'}
                    </div>
                    {useIndividualReasons && (
                      <div className="stack" style={{ gap: 8, marginTop: 10 }}>
                        <label className="field">
                          <span>Lý do riêng (hoặc dùng lý do chung)</span>
                          <select
                            className="select"
                            value={individual.reason}
                            onChange={(event) => setIndividualReasons((current) => ({
                              ...current,
                              [enrollment.id]: { ...individual, reason: event.target.value, other: '' },
                            }))}
                          >
                            <option value="">Dùng lý do chung</option>
                            {REJECTION_REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                          </select>
                        </label>
                        {individual.reason === 'OTHER' && (
                          <textarea
                            className="input"
                            rows={2}
                            maxLength={500}
                            value={individual.other}
                            onChange={(event) => setIndividualReasons((current) => ({
                              ...current,
                              [enrollment.id]: { ...individual, other: event.target.value },
                            }))}
                            placeholder="Nhập lý do riêng cụ thể"
                          />
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
            {rejectDialog.isBulk && rejectDialog.preview?.results?.some((item) => !item.eligible) && (
              <div className="notice notice-warning">
                Có yêu cầu không còn chờ duyệt hoặc không được tìm thấy. Backend sẽ bỏ qua chúng và ghi rõ lý do trong lịch sử.
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        open={historyDetail !== null || historyDetailLoading}
        onClose={() => {
          if (!historyDetailLoading) setHistoryDetail(null);
        }}
        title="Chi tiết đợt xử lý"
        subtitle={historyDetail?.operation?.batchCode}
        size="xl"
      >
        {historyDetailLoading ? <LoadingBlock text="Đang tải chi tiết..." /> : historyDetail && (
          <div className="stack" style={{ gap: 12 }}>
            <div className="card" style={{ padding: 14 }}>
              <div><strong>ADMIN:</strong> {historyDetail.operation.adminUsername}</div>
              <div><strong>Thời gian:</strong> {formatDateTime(historyDetail.operation.createdAt)}</div>
              <div><strong>Học kỳ:</strong> {historyDetail.operation.semesterName || 'Nhiều học kỳ / không xác định'}</div>
              <div><strong>Thao tác:</strong> {historyDetail.operation.action === 'APPROVE' ? 'Duyệt' : 'Từ chối'}</div>
              {historyDetail.operation.rejectionReason && (
                <div><strong>Lý do chung:</strong> {historyDetail.operation.rejectionReason}</div>
              )}
            </div>
            <div className="stats-grid">
              <div className="stat-card"><div className="stat-info"><small>Đã chọn</small><strong>{historyDetail.operation.selectedCount}</strong></div></div>
              <div className="stat-card"><div className="stat-info"><small>Thành công</small><strong>{historyDetail.operation.successCount}</strong></div></div>
              <div className="stat-card"><div className="stat-info"><small>Thất bại</small><strong>{historyDetail.operation.failureCount}</strong></div></div>
              <div className="stat-card"><div className="stat-info"><small>Bỏ qua</small><strong>{historyDetail.operation.skippedCount}</strong></div></div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr><th>Mã SV / Họ tên</th><th>Môn / Lớp</th><th>Học kỳ</th><th>Thao tác</th><th>Kết quả</th><th>Lý do</th></tr>
                </thead>
                <tbody>
                  {historyDetail.results.map((item, index) => (
                    <tr key={`${item.enrollmentId}-${index}`}>
                      <td>{item.studentCode || '—'}<div className="cell-muted">{item.studentName || `#${item.enrollmentId}`}</div></td>
                      <td>{item.courseCode || '—'} · {item.courseName || '—'}<div className="cell-muted">{item.sectionCode || '—'}</div></td>
                      <td>{item.semesterName || '—'}</td>
                      <td>{item.action === 'APPROVE' ? 'Duyệt' : 'Từ chối'}</td>
                      <td>{item.resultStatus}</td>
                      <td>{item.success ? item.rejectionReason || item.message : item.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
};

export default AdminEnrollments;
