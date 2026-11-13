import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { feedbackApi, lecturerApi } from '../../api';
import { usePaginatedList } from '../../hooks/usePaginatedList';
import { ClockIcon, FileTextIcon, RefreshIcon, SupportIcon, UsersIcon } from '../../components/Icons';
import { useToast } from '../../context/ToastContext';
import { EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatCard, StatusBadge } from '../../components/UI';
import { formatDateTime, getErrorMessage } from '../../utils/format';

const FEEDBACK_TYPE_OPTIONS = [
    { value: '', label: 'Tất cả loại' },
    { value: 'SYSTEM_ERROR', label: 'Lỗi hệ thống' },
    { value: 'ACCOUNT', label: 'Tài khoản' },
    { value: 'GRADE', label: 'Điểm số' },
    { value: 'TEACHING', label: 'Chất lượng giảng dạy' },
    { value: 'SCHEDULE', label: 'Lịch học' },
    { value: 'ACADEMIC', label: 'Học vụ' },
    { value: 'OTHER', label: 'Khác' },
];

const STATUS_OPTIONS = [
    { value: '', label: 'Tất cả trạng thái' },
    { value: 'PENDING', label: 'Chờ tiếp nhận' },
    { value: 'IN_PROGRESS', label: 'Đang xử lý' },
    { value: 'RESPONDED', label: 'Đã phản hồi' },
    { value: 'RESOLVED', label: 'Đã giải quyết' },
    { value: 'REJECTED', label: 'Từ chối xử lý' },
];

const statusLabelMap = {
    PENDING: 'Chờ tiếp nhận',
    IN_PROGRESS: 'Đang xử lý',
    RESPONDED: 'Đã phản hồi',
    RESOLVED: 'Đã giải quyết',
    REJECTED: 'Từ chối xử lý',
};

const feedbackTypeLabel = (value) => FEEDBACK_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || 'Khác';
const messageRoleLabel = (role) => ({ STUDENT: 'Sinh viên', LECTURER: 'Giảng viên', ADMIN: 'Quản trị viên' }[role] || role || 'Hệ thống');

const AdminFeedbacks = () => {
    const toast = useToast();
    const [tickets, setTickets] = useState([]);
    const [lecturers, setLecturers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedTicket, setSelectedTicket] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [statusFilter, setStatusFilter] = useState('');
    const [typeFilter, setTypeFilter] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [assigning, setAssigning] = useState(false);
    const [selectedLecturerId, setSelectedLecturerId] = useState('');
    const [adminNote, setAdminNote] = useState('');
    const [replyText, setReplyText] = useState('');
    const [sendingReply, setSendingReply] = useState(false);
    const [statusValue, setStatusValue] = useState('IN_PROGRESS');
    const [statusNote, setStatusNote] = useState('');
    const [updatingStatus, setUpdatingStatus] = useState(false);

    const loadTickets = async () => {
        setLoading(true);
        try {
            const response = await feedbackApi.getAll(statusFilter || undefined, typeFilter || undefined);
            setTickets(response.data || []);
        } catch (error) {
            toast.error('Không thể tải danh sách phản ánh', getErrorMessage(error));
        } finally {
            setLoading(false);
        }
    };

    const loadLecturers = async () => {
        try {
            const response = await lecturerApi.getAll();
            setLecturers(response.data || []);
        } catch (error) {
            toast.error('Không thể tải danh sách giảng viên', getErrorMessage(error));
        }
    };

    useEffect(() => { loadTickets(); }, [statusFilter, typeFilter]);
    useEffect(() => { loadLecturers(); }, []);

    const totals = useMemo(() => ({
        total: tickets.length,
        pending: tickets.filter((item) => item.status === 'PENDING').length,
        inProgress: tickets.filter((item) => item.status === 'IN_PROGRESS').length,
        resolved: tickets.filter((item) => item.status === 'RESOLVED').length,
    }), [tickets]);

    const filteredTickets = useMemo(() => {
        const normalized = searchTerm.trim().toLowerCase();
        if (!normalized) return tickets;
        return tickets.filter((ticket) => [
            ticket.title,
            ticket.studentName,
            ticket.assignedLecturerName,
            ticket.content,
            String(ticket.id),
        ].some((value) => String(value || '').toLowerCase().includes(normalized)));
    }, [tickets, searchTerm]);
    const { page, setPage, pageSize, setPageSize, totalItems, pageItems } = usePaginatedList(filteredTickets);

    const openTicket = async (ticketId) => {
        setDetailLoading(true);
        try {
            const response = await feedbackApi.getById(ticketId);
            const nextTicket = response.data;
            setSelectedTicket(nextTicket);
            setSelectedLecturerId(nextTicket.assignedLecturerId ? String(nextTicket.assignedLecturerId) : '');
            setAdminNote(nextTicket.adminNote || '');
            setStatusValue(nextTicket.status || 'IN_PROGRESS');
        } catch (error) {
            toast.error('Không thể mở chi tiết phản ánh', getErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    };

    const clearFilters = () => {
        setStatusFilter('');
        setTypeFilter('');
        setSearchTerm('');
        setPage(1);
    };

    const handleAssign = async () => {
        if (!selectedTicket || !selectedLecturerId) {
            toast.error('Thiếu giảng viên', 'Vui lòng chọn giảng viên để phân công.');
            return;
        }

        const result = await Swal.fire({
            title: 'Phân công giảng viên?',
            text: `Bạn sẽ giao phản ánh #${selectedTicket.id} cho giảng viên đã chọn.`,
            icon: 'question',
            showCancelButton: true,
            confirmButtonText: 'Phân công',
            cancelButtonText: 'Hủy',
            reverseButtons: true,
        });
        if (!result.isConfirmed) return;

        setAssigning(true);
        try {
            const response = await feedbackApi.assign(selectedTicket.id, {
                lecturerId: Number(selectedLecturerId),
                adminNote: adminNote.trim() || undefined,
            });
            setSelectedTicket(response.data);
            loadTickets();
            toast.success('Phân công thành công', 'Giảng viên sẽ nhận thông báo và theo dõi tiến độ xử lý.');
        } catch (error) {
            toast.error('Phân công thất bại', getErrorMessage(error));
        } finally {
            setAssigning(false);
        }
    };

    const handleStatusUpdate = async () => {
        if (!selectedTicket) return;
        const result = await Swal.fire({
            title: 'Cập nhật trạng thái?',
            text: `Bạn sẽ đặt phản ánh #${selectedTicket.id} sang trạng thái ${statusLabelMap[statusValue] || statusValue}.`,
            icon: 'question',
            showCancelButton: true,
            cancelButtonText: 'Hủy',
            confirmButtonText: 'Xác nhận',
            reverseButtons: true,
        });
        if (!result.isConfirmed) return;

        setUpdatingStatus(true);
        try {
            const response = await feedbackApi.updateStatus(selectedTicket.id, { status: statusValue, note: statusNote.trim() || undefined });
            setSelectedTicket(response.data);
            setStatusNote('');
            loadTickets();
            toast.success('Cập nhật trạng thái thành công', 'Sinh viên và giảng viên đã được cập nhật tiến độ.');
        } catch (error) {
            toast.error('Cập nhật trạng thái thất bại', getErrorMessage(error));
        } finally {
            setUpdatingStatus(false);
        }
    };

    const handleReply = async () => {
        if (!selectedTicket || !replyText.trim()) {
            toast.error('Thiếu nội dung', 'Vui lòng nhập tin nhắn cho quá trình xử lý.');
            return;
        }

        setSendingReply(true);
        try {
            const response = await feedbackApi.addMessage(selectedTicket.id, { message: replyText.trim() });
            setSelectedTicket((prev) => prev ? { ...prev, messages: [...(prev.messages || []), response.data] } : prev);
            setReplyText('');
            loadTickets();
            toast.success('Gửi tin nhắn thành công', 'Lịch sử trao đổi đã được cập nhật.');
        } catch (error) {
            toast.error('Không thể gửi tin nhắn', getErrorMessage(error));
        } finally {
            setSendingReply(false);
        }
    };

    return (
        <>
            <PageHeader
                title="Quản lý phản ánh"
                subtitle="Theo dõi, phân công và xử lý các phản ánh của sinh viên trong hệ thống."
                actions={(
                    <button className="btn btn-secondary" onClick={() => { loadTickets(); loadLecturers(); }}>
                        <RefreshIcon /> Làm mới
                    </button>
                )}
            />

            <div className="stats-grid">
                <StatCard label="Tổng phản ánh" value={totals.total} icon={<SupportIcon />} tone="blue" />
                <StatCard label="Chờ tiếp nhận" value={totals.pending} icon={<ClockIcon />} tone="amber" />
                <StatCard label="Đang xử lý" value={totals.inProgress} icon={<UsersIcon />} tone="indigo" />
                <StatCard label="Đã giải quyết" value={totals.resolved} icon={<FileTextIcon />} tone="green" />
            </div>

            <div className="card">
                <div className="card-head">
                    <div>
                        <h2>Danh sách phản ánh</h2>
                        <p>Quản lý toàn bộ yêu cầu hỗ trợ và tiến độ xử lý của hệ thống.</p>
                    </div>
                </div>
                <div className="card-body">
                    <div className="toolbar">
                        <SearchInput value={searchTerm} onChange={(value) => { setSearchTerm(value); setPage(1); }} placeholder="Tìm mã, tiêu đề, nội dung hoặc sinh viên..." />
                        <select className="select" style={{ width: 200 }} value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1); }}>
                            {STATUS_OPTIONS.map((option) => <option key={option.value || 'all-status'} value={option.value}>{option.label}</option>)}
                        </select>
                        <select className="select" style={{ width: 210 }} value={typeFilter} onChange={(event) => { setTypeFilter(event.target.value); setPage(1); }}>
                            {FEEDBACK_TYPE_OPTIONS.map((option) => <option key={option.value || 'all-type'} value={option.value}>{option.label}</option>)}
                        </select>
                        <button className="btn btn-secondary" onClick={clearFilters}>Xóa lọc</button>
                        <div className="toolbar-spacer" />
                        <span className="badge badge-gray">{filteredTickets.length} phản ánh</span>
                    </div>

                    {loading ? (
                        <LoadingBlock text="Đang tải dữ liệu phản ánh..." />
                    ) : filteredTickets.length === 0 ? (
                        <EmptyState
                            icon={<SupportIcon />}
                            title="Không tìm thấy phản ánh"
                            description="Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm để xem thêm dữ liệu."
                        />
                    ) : (
                        <div className="table-wrap">
                            <table className="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã</th>
                                        <th>Tiêu đề</th>
                                        <th>Sinh viên</th>
                                        <th>Loại</th>
                                        <th>Giảng viên</th>
                                        <th>Trạng thái</th>
                                        <th>Ngày gửi</th>
                                        <th style={{ textAlign: 'right' }}>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {pageItems.map((ticket) => (
                                        <tr key={ticket.id}>
                                            <td className="cell-strong">#{ticket.id}</td>
                                            <td>
                                                <div className="cell-strong">{ticket.title}</div>
                                                <div className="cell-muted">{ticket.content?.slice(0, 72) || '—'}{ticket.content && ticket.content.length > 72 ? '...' : ''}</div>
                                            </td>
                                            <td>{ticket.studentName || '—'}</td>
                                            <td>{feedbackTypeLabel(ticket.feedbackType)}</td>
                                            <td>{ticket.assignedLecturerName || <span className="badge badge-amber">Chưa phân công</span>}</td>
                                            <td>
                                                <StatusBadge status={ticket.status} label={statusLabelMap[ticket.status] || ticket.status || 'Chưa rõ'} />
                                            </td>
                                            <td>{formatDateTime(ticket.createdAt)}</td>
                                            <td>
                                                <div className="row-actions">
                                                    <button className="btn btn-secondary btn-sm" onClick={() => openTicket(ticket.id)}>
                                                        Xem
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                    {!loading && (
                        <Pagination
                            page={page}
                            pageSize={pageSize}
                            totalItems={totalItems}
                            onPageChange={setPage}
                            onPageSizeChange={setPageSize}
                        />
                    )}
                </div>
            </div>

            <Modal
                open={Boolean(selectedTicket)}
                onClose={() => setSelectedTicket(null)}
                title="Chi tiết phản ánh"
                subtitle={selectedTicket ? (
                    <span className="modal-ticket-subtitle">
                        <span className="modal-ticket-id">#{selectedTicket.id}</span>
                        <span className="modal-ticket-separator">•</span>
                        <span>{selectedTicket.title}</span>
                        <StatusBadge status={selectedTicket.status} label={statusLabelMap[selectedTicket.status] || selectedTicket.status || 'Chưa rõ'} />
                    </span>
                ) : ''}
                size="xl"
                footer={null}
            >
                {detailLoading ? (
                    <LoadingBlock text="Đang tải chi tiết phản ánh..." />
                ) : selectedTicket ? (
                    <div className="detail-shell">
                        <div className="detail-content-grid">
                            <div className="detail-column-main">
                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Thông tin người gửi</h3>
                                    </div>
                                    <div className="student-summary">
                                        <div className="avatar avatar-lg">{(selectedTicket.studentName || 'S').charAt(0).toUpperCase()}</div>
                                        <div className="student-summary-meta">
                                            <strong>{selectedTicket.studentName || 'Sinh viên chưa cập nhật'}</strong>
                                            <span>{selectedTicket.studentCode || 'Mã sinh viên: —'}</span>
                                            <span>{selectedTicket.studentEmail || 'Email: —'}</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Nội dung phản ánh</h3>
                                    </div>
                                    <div className="content-card">
                                        <div className="content-card-header">
                                            <span className="content-card-title">{selectedTicket.title}</span>
                                            <span className="content-card-time">{formatDateTime(selectedTicket.createdAt)}</span>
                                        </div>
                                        <div className="content-body">{selectedTicket.content}</div>
                                        {selectedTicket.attachmentUrl ? (
                                            <div className="attachment-row">
                                                <a href={selectedTicket.attachmentUrl} target="_blank" rel="noreferrer">Xem file đính kèm</a>
                                            </div>
                                        ) : null}
                                    </div>

                                    {selectedTicket.adminNote && (
                                        <div className="content-note">
                                            <strong>Ghi chú quản trị</strong>
                                            <p>{selectedTicket.adminNote}</p>
                                        </div>
                                    )}
                                </div>

                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Trao đổi & phản hồi</h3>
                                    </div>
                                    <div className="chat-thread">
                                        {(selectedTicket.messages || []).length === 0 ? (
                                            <div className="empty-chat">Chưa có tin nhắn nào.</div>
                                        ) : (
                                            (selectedTicket.messages || []).map((message) => (
                                                <div key={message.id} className={`chat-bubble ${message.senderRole === 'ADMIN' ? 'outgoing' : 'incoming'}`}>
                                                    <div className="chat-head">
                                                        <div className="chat-user">
                                                            <span className="avatar avatar-sm">{(message.senderFullName || 'U').charAt(0).toUpperCase()}</span>
                                                            <strong>{message.senderFullName || 'Người dùng'}</strong>
                                                        </div>
                                                        <span>{messageRoleLabel(message.senderRole)}</span>
                                                    </div>
                                                    <p>{message.message}</p>
                                                    <small>{formatDateTime(message.createdAt)}</small>
                                                </div>
                                            ))
                                        )}
                                    </div>
                                </div>
                            </div>

                            <div className="detail-column-side">
                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Phân công xử lý</h3>
                                    </div>
                                    <div className="assignment-box">
                                        <div className="mentor-summary">
                                            <div className="avatar avatar-md">{(selectedTicket.assignedLecturerName || 'L').charAt(0).toUpperCase()}</div>
                                            <div>
                                                <strong>{selectedTicket.assignedLecturerName || 'Chưa phân công'}</strong>
                                                <span>{selectedTicket.assignedLecturerCode || 'Giảng viên phụ trách'}</span>
                                            </div>
                                        </div>
                                        <div className="detail-form-grid compact">
                                            <Field label="Giảng viên phụ trách">
                                                <select className="input" value={selectedLecturerId} onChange={(event) => setSelectedLecturerId(event.target.value)}>
                                                    <option value="">Chọn giảng viên...</option>
                                                    {lecturers.map((lecturer) => (
                                                        <option key={lecturer.id} value={String(lecturer.id)}>
                                                            {lecturer.fullName || lecturer.lecturerCode || lecturer.username}
                                                        </option>
                                                    ))}
                                                </select>
                                            </Field>
                                            <Field label="Ghi chú quản trị">
                                                <input className="input" value={adminNote} onChange={(event) => setAdminNote(event.target.value)} placeholder="Nhập ghi chú..." />
                                            </Field>
                                        </div>
                                        <div className="detail-actions-row">
                                            <button className="btn btn-primary" onClick={handleAssign} disabled={assigning}>{assigning ? 'Đang phân công...' : 'Phân công giảng viên'}</button>
                                        </div>
                                    </div>
                                </div>

                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Tiến độ xử lý</h3>
                                    </div>
                                    <div className="timeline">
                                        {['PENDING', 'IN_PROGRESS', 'RESPONDED', 'RESOLVED'].map((step, index) => {
                                            const currentIndex = ['PENDING', 'IN_PROGRESS', 'RESPONDED', 'RESOLVED'].indexOf(selectedTicket.status);
                                            const isPast = currentIndex >= index;
                                            const isCurrent = selectedTicket.status === step;
                                            return (
                                                <div key={step} className={`timeline-item ${isPast ? 'done' : ''} ${isCurrent ? 'active' : ''}`}>
                                                    <div className="timeline-marker" aria-hidden="true" />
                                                    <div className="timeline-content">
                                                        <div className="timeline-copy">
                                                            <strong>{step === 'PENDING' ? 'Đã gửi phản ánh' : step === 'IN_PROGRESS' ? 'Đã tiếp nhận' : step === 'RESPONDED' ? 'Đã phản hồi' : 'Hoàn thành'}</strong>
                                                            <small>{isCurrent ? 'Đang diễn ra' : isPast ? 'Đã hoàn tất' : 'Chưa bắt đầu'}</small>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                <div className="detail-panel">
                                    <div className="detail-panel-head">
                                        <h3>Thao tác xử lý</h3>
                                    </div>
                                    <div className="reply-box">
                                        <div className="detail-form-grid compact">
                                            <Field label="Cập nhật trạng thái">
                                                <select className="input" value={statusValue} onChange={(event) => setStatusValue(event.target.value)}>
                                                    {STATUS_OPTIONS.filter((option) => option.value).map((option) => (
                                                        <option key={option.value} value={option.value}>{option.label}</option>
                                                    ))}
                                                </select>
                                            </Field>
                                            <Field label="Ghi chú">
                                                <input className="input" value={statusNote} onChange={(event) => setStatusNote(event.target.value)} placeholder="Nhập ghi chú..." />
                                            </Field>
                                        </div>
                                        <div className="detail-actions-row">
                                            <button className="btn btn-primary" onClick={handleStatusUpdate} disabled={updatingStatus}>{updatingStatus ? 'Đang cập nhật...' : 'Cập nhật trạng thái'}</button>
                                        </div>

                                        <Field label="Thêm tin nhắn">
                                            <textarea rows={4} className="input" value={replyText} onChange={(event) => setReplyText(event.target.value)} placeholder="Nhập phản hồi hoặc hướng dẫn xử lý..." />
                                        </Field>
                                        <div className="detail-actions-row">
                                            <button className="btn btn-primary" onClick={handleReply} disabled={sendingReply}>{sendingReply ? 'Đang gửi...' : 'Gửi tin nhắn'}</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                ) : null}
            </Modal>
        </>
    );
};

export default AdminFeedbacks;
