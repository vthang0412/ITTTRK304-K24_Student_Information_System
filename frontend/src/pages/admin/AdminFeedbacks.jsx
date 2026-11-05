import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { feedbackApi, lecturerApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { Field, LoadingBlock, Modal, SearchInput } from '../../components/UI';
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
    { value: 'RESOLVED', label: 'Đã hoàn thành' },
    { value: 'REJECTED', label: 'Từ chối xử lý' },
];

const statusTone = {
    PENDING: 'badge-amber',
    IN_PROGRESS: 'badge-blue',
    RESPONDED: 'badge-purple',
    RESOLVED: 'badge-green',
    REJECTED: 'badge-red',
};

const statusLabelMap = {
    PENDING: 'Chờ tiếp nhận',
    IN_PROGRESS: 'Đang xử lý',
    RESPONDED: 'Đã phản hồi',
    RESOLVED: 'Đã hoàn thành',
    REJECTED: 'Từ chối xử lý',
};

const feedbackTypeLabel = (value) => FEEDBACK_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || 'Khác';
const messageRoleLabel = (role) => ({ STUDENT: 'Sinh viên', LECTURER: 'Giảng viên', ADMIN: 'Quản trị viên' }[role] || role || 'Hệ thống');

const TicketBadge = ({ status }) => (
    <span className={`badge ${statusTone[status] || 'badge-gray'}`}>{statusLabelMap[status] || status || 'Chưa rõ'}</span>
);

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
            toast.error('Không thể tải danh sách ticket', getErrorMessage(error));
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
            toast.error('Không thể mở chi tiết ticket', getErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    };

    const clearFilters = () => {
        setStatusFilter('');
        setTypeFilter('');
        setSearchTerm('');
    };

    const handleAssign = async () => {
        if (!selectedTicket || !selectedLecturerId) {
            toast.error('Thiếu giảng viên', 'Vui lòng chọn giảng viên để phân công.');
            return;
        }

        const result = await Swal.fire({
            title: 'Phân công giảng viên?',
            text: `Bạn sẽ giao ticket #${selectedTicket.id} cho giảng viên đã chọn.`,
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
            toast.success('Phân công thành công', 'Giảng viên được thông báo và sinh viên sẽ theo dõi tiến độ.');
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
            text: `Bạn sẽ đặt ticket #${selectedTicket.id} sang trạng thái ${statusLabelMap[statusValue] || statusValue}.`,
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
            toast.success('Cập nhật trạng thái thành công', 'Sinh viên và giảng viên đã được thông báo.');
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
        <div className="feedback-page">
            <div className="feedback-hero">
                <div>
                    <span className="eyebrow">Administration</span>
                    <h1>Quản lý phản ánh</h1>
                    <p>Theo dõi toàn bộ ticket, phân công giảng viên và kiểm soát tiến độ xử lý.</p>
                </div>
            </div>

            <div className="feedback-stats">
                <div className="feedback-stat stat-blue">
                    <div className="feedback-stat-icon"><span>#</span></div>
                    <div>
                        <small>Tổng ticket</small>
                        <strong>{tickets.length}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-amber">
                    <div className="feedback-stat-icon"><span>⏳</span></div>
                    <div>
                        <small>Chờ tiếp nhận</small>
                        <strong>{tickets.filter((item) => item.status === 'PENDING').length}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-indigo">
                    <div className="feedback-stat-icon"><span>⚙</span></div>
                    <div>
                        <small>Đang xử lý</small>
                        <strong>{tickets.filter((item) => item.status === 'IN_PROGRESS').length}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-green">
                    <div className="feedback-stat-icon"><span>✓</span></div>
                    <div>
                        <small>Đã hoàn thành</small>
                        <strong>{tickets.filter((item) => item.status === 'RESOLVED').length}</strong>
                    </div>
                </div>
            </div>

            <div className="card feedback-panel">
                <div className="card-head">
                    <div>
                        <h2>Danh sách phản ánh</h2>
                        <p>Quản lý toàn bộ yêu cầu hỗ trợ của hệ thống.</p>
                    </div>
                </div>
                <div className="card-body">
                    <div className="feedback-toolbar">
                        <SearchInput value={searchTerm} onChange={setSearchTerm} placeholder="Tìm kiếm theo tiêu đề, sinh viên, giảng viên..." />
                        <select className="input" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                            {STATUS_OPTIONS.map((option) => <option key={option.value || 'all-status'} value={option.value}>{option.label}</option>)}
                        </select>
                        <select className="input" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
                            {FEEDBACK_TYPE_OPTIONS.map((option) => <option key={option.value || 'all-type'} value={option.value}>{option.label}</option>)}
                        </select>
                        <button className="btn btn-secondary" onClick={clearFilters}>Xóa lọc</button>
                    </div>

                    {loading ? (
                        <div className="empty-state"><LoadingBlock text="Đang tải dữ liệu phản ánh..." /></div>
                    ) : filteredTickets.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">✦</div>
                            <h3>Không tìm thấy phản ánh</h3>
                            <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm để xem thêm dữ liệu.</p>
                        </div>
                    ) : (
                        <div className="feedback-table-wrap">
                            <table className="data-table feedback-table">
                                <thead>
                                    <tr>
                                        <th>Mã</th>
                                        <th>Tiêu đề</th>
                                        <th>Sinh viên</th>
                                        <th>Loại</th>
                                        <th>Giảng viên</th>
                                        <th>Trạng thái</th>
                                        <th>Ngày gửi</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredTickets.map((ticket) => (
                                        <tr key={ticket.id}>
                                            <td className="cell-strong">#{ticket.id}</td>
                                            <td>
                                                <div className="ticket-title-stack">
                                                    <strong>{ticket.title}</strong>
                                                </div>
                                            </td>
                                            <td>{ticket.studentName || '—'}</td>
                                            <td>{feedbackTypeLabel(ticket.feedbackType)}</td>
                                            <td>{ticket.assignedLecturerName || 'Chưa phân công'}</td>
                                            <td><TicketBadge status={ticket.status} /></td>
                                            <td>{formatDateTime(ticket.createdAt)}</td>
                                            <td><button className="btn btn-secondary btn-sm" onClick={() => openTicket(ticket.id)}>Xem</button></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>

            <Modal open={Boolean(selectedTicket)} onClose={() => setSelectedTicket(null)} title={selectedTicket ? `Ticket #${selectedTicket.id}` : 'Chi tiết'} subtitle={selectedTicket ? selectedTicket.title : ''} size="lg" footer={null}>
                {detailLoading ? (
                    <LoadingBlock text="Đang tải chi tiết ticket..." />
                ) : selectedTicket ? (
                    <div className="feedback-detail-layout">
                        <div className="feedback-detail-panel">
                            <div className="detail-topbar">
                                <div>
                                    <span className="detail-kicker">Thông tin phản ánh</span>
                                    <h3>{selectedTicket.title}</h3>
                                </div>
                                <TicketBadge status={selectedTicket.status} />
                            </div>

                            <div className="detail-grid">
                                <div className="meta-card">
                                    <span>Sinh viên</span>
                                    <strong>{selectedTicket.studentName || '—'}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Giảng viên phụ trách</span>
                                    <strong>{selectedTicket.assignedLecturerName || 'Chưa phân công'}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Loại</span>
                                    <strong>{feedbackTypeLabel(selectedTicket.feedbackType)}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Ngày gửi</span>
                                    <strong>{formatDateTime(selectedTicket.createdAt)}</strong>
                                </div>
                            </div>

                            <div className="detail-block">
                                <label>Nội dung phản ánh</label>
                                <div className="text-box">{selectedTicket.content}</div>
                            </div>

                            {selectedTicket.adminNote && (
                                <div className="detail-block">
                                    <label>Ghi chú quản trị</label>
                                    <div className="text-box text-box-soft">{selectedTicket.adminNote}</div>
                                </div>
                            )}
                        </div>

                        <div className="feedback-detail-panel">
                            <div className="timeline-header">Phân công giảng viên</div>
                            <div className="detail-form-grid">
                                <Field label="Giảng viên phụ trách">
                                    <select className="input" value={selectedLecturerId} onChange={(event) => setSelectedLecturerId(event.target.value)}>
                                        <option value="">Chọn giảng viên...</option>
                                        {lecturers.map((lecturer) => (
                                            <option key={lecturer.id} value={String(lecturer.id)}>{lecturer.fullName || lecturer.lecturerCode || lecturer.username}</option>
                                        ))}
                                    </select>
                                </Field>
                                <Field label="Ghi chú quản trị">
                                    <input className="input" value={adminNote} onChange={(event) => setAdminNote(event.target.value)} placeholder="Nhập ghi chú..." />
                                </Field>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                <button className="btn btn-primary" onClick={handleAssign} disabled={assigning}>{assigning ? 'Đang phân công...' : 'Phân công giảng viên'}</button>
                            </div>
                        </div>

                        <div className="feedback-detail-panel">
                            <div className="timeline-header">Tiến độ xử lý</div>
                            <div className="timeline">
                                {['PENDING', 'IN_PROGRESS', 'RESPONDED', 'RESOLVED'].map((step, index) => {
                                    const currentIndex = ['PENDING', 'IN_PROGRESS', 'RESPONDED', 'RESOLVED'].indexOf(selectedTicket.status);
                                    const isPast = currentIndex >= index;
                                    const isCurrent = selectedTicket.status === step;
                                    return (
                                        <div key={step} className={`timeline-item ${isPast ? 'done' : ''} ${isCurrent ? 'active' : ''}`}>
                                            <div className="timeline-dot" />
                                            <div className="timeline-copy">
                                                <strong>{step === 'PENDING' ? 'Đã gửi phản ánh' : step === 'IN_PROGRESS' ? 'Đã tiếp nhận' : step === 'RESPONDED' ? 'Đã phản hồi' : 'Hoàn thành'}</strong>
                                                <small>{isCurrent ? 'Đang diễn ra' : isPast ? 'Đã hoàn tất' : 'Chưa bắt đầu'}</small>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        <div className="feedback-detail-panel feedback-chat-panel">
                            <div className="timeline-header">Lịch sử trao đổi</div>
                            <div className="chat-thread">
                                {(selectedTicket.messages || []).length === 0 ? (
                                    <div className="empty-chat">Chưa có tin nhắn nào.</div>
                                ) : (
                                    (selectedTicket.messages || []).map((message) => (
                                        <div key={message.id} className={`chat-bubble ${message.senderRole === 'ADMIN' ? 'outgoing' : 'incoming'}`}>
                                            <div className="chat-head">
                                                <strong>{message.senderFullName || 'Người dùng'}</strong>
                                                <span>{messageRoleLabel(message.senderRole)}</span>
                                            </div>
                                            <p>{message.message}</p>
                                            <small>{formatDateTime(message.createdAt)}</small>
                                        </div>
                                    ))
                                )}
                            </div>

                            <div className="reply-box">
                                <div className="detail-form-grid">
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
                                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                    <button className="btn btn-primary" onClick={handleStatusUpdate} disabled={updatingStatus}>{updatingStatus ? 'Đang cập nhật...' : 'Cập nhật trạng thái'}</button>
                                </div>

                                <Field label="Thêm tin nhắn">
                                    <textarea rows={4} className="input" value={replyText} onChange={(event) => setReplyText(event.target.value)} placeholder="Nhập phản hồi hoặc hướng dẫn xử lý..." />
                                </Field>
                                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                    <button className="btn btn-primary" onClick={handleReply} disabled={sendingReply}>{sendingReply ? 'Đang gửi...' : 'Gửi tin nhắn'}</button>
                                </div>
                            </div>
                        </div>
                    </div>
                ) : null}
            </Modal>
        </div>
    );
};

export default AdminFeedbacks;
