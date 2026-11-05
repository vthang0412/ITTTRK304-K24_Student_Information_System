import React, { useEffect, useState } from 'react';
import Swal from 'sweetalert2';
import { feedbackApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { Field, LoadingBlock, Modal } from '../../components/UI';
import { formatDateTime, getErrorMessage } from '../../utils/format';

const FEEDBACK_TYPE_OPTIONS = [
    { value: 'SYSTEM_ERROR', label: 'Lỗi hệ thống' },
    { value: 'ACCOUNT', label: 'Tài khoản' },
    { value: 'GRADE', label: 'Điểm số' },
    { value: 'TEACHING', label: 'Chất lượng giảng dạy' },
    { value: 'SCHEDULE', label: 'Lịch học' },
    { value: 'ACADEMIC', label: 'Học vụ' },
    { value: 'OTHER', label: 'Khác' },
];

const STATUS_OPTIONS = [
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

const LecturerFeedbacks = () => {
    const toast = useToast();
    const [tickets, setTickets] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedTicket, setSelectedTicket] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [sendingReply, setSendingReply] = useState(false);
    const [replyText, setReplyText] = useState('');
    const [statusValue, setStatusValue] = useState('IN_PROGRESS');
    const [statusNote, setStatusNote] = useState('');
    const [updating, setUpdating] = useState(false);

    const loadTickets = async () => {
        setLoading(true);
        try {
            const response = await feedbackApi.getAssigned();
            setTickets(response.data || []);
        } catch (error) {
            toast.error('Không thể tải ticket được giao', getErrorMessage(error));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { loadTickets(); }, []);

    const openTicket = async (ticketId) => {
        setDetailLoading(true);
        try {
            const response = await feedbackApi.getById(ticketId);
            setSelectedTicket(response.data);
            setStatusValue(response.data.status || 'IN_PROGRESS');
        } catch (error) {
            toast.error('Không thể mở chi tiết', getErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    };

    const handleStatusUpdate = async () => {
        if (!selectedTicket) return;
        const result = await Swal.fire({
            title: 'Xác nhận cập nhật trạng thái?',
            text: `Bạn sẽ cập nhật trạng thái phản ánh #${selectedTicket.id} thành ${statusLabelMap[statusValue] || statusValue}.`,
            icon: 'question',
            showCancelButton: true,
            confirmButtonText: 'Cập nhật',
            cancelButtonText: 'Hủy',
            reverseButtons: true,
        });
        if (!result.isConfirmed) return;

        setUpdating(true);
        try {
            const response = await feedbackApi.updateStatus(selectedTicket.id, { status: statusValue, note: statusNote.trim() || undefined });
            setSelectedTicket(response.data);
            setStatusNote('');
            loadTickets();
            toast.success('Cập nhật trạng thái thành công', 'Sinh viên đã được thông báo về tiến độ xử lý.');
        } catch (error) {
            toast.error('Cập nhật trạng thái thất bại', getErrorMessage(error));
        } finally {
            setUpdating(false);
        }
    };

    const submitReply = async () => {
        if (!selectedTicket || !replyText.trim()) {
            toast.error('Thiếu nội dung', 'Vui lòng nhập phản hồi cho sinh viên.');
            return;
        }

        setSendingReply(true);
        try {
            const response = await feedbackApi.addMessage(selectedTicket.id, { message: replyText.trim() });
            const updatedMessages = [...(selectedTicket.messages || []), response.data];
            setSelectedTicket((prev) => prev ? { ...prev, messages: updatedMessages, status: prev.status === 'PENDING' ? 'RESPONDED' : prev.status } : prev);
            setReplyText('');
            toast.success('Đã gửi phản hồi', 'Sinh viên sẽ nhận được tin nhắn mới.');
            loadTickets();
        } catch (error) {
            toast.error('Không thể gửi phản hồi', getErrorMessage(error));
        } finally {
            setSendingReply(false);
        }
    };

    return (
        <div className="feedback-page">
            <div className="feedback-hero">
                <div>
                    <span className="eyebrow">Support queue</span>
                    <h1>Ticket được phân công</h1>
                    <p>Quản lý phản ánh, phản hồi sinh viên và theo dõi tiến độ xử lý.</p>
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
                        <small>Chờ xử lý</small>
                        <strong>{tickets.filter((item) => item.status === 'PENDING' || item.status === 'IN_PROGRESS').length}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-indigo">
                    <div className="feedback-stat-icon"><span>💬</span></div>
                    <div>
                        <small>Đã phản hồi</small>
                        <strong>{tickets.filter((item) => item.status === 'RESPONDED' || item.status === 'RESOLVED').length}</strong>
                    </div>
                </div>
            </div>

            <div className="card feedback-panel">
                <div className="card-head">
                    <div>
                        <h2>Danh sách ticket</h2>
                        <p>Các phản ánh được admin giao cho bạn.</p>
                    </div>
                </div>
                <div className="card-body">
                    {loading ? (
                        <div className="empty-state"><LoadingBlock text="Đang tải danh sách ticket..." /></div>
                    ) : tickets.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">✓</div>
                            <h3>Chưa có ticket nào</h3>
                            <p>Phiếu phản ánh mới sẽ xuất hiện ở đây khi được admin phân công.</p>
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
                                        <th>Ngày cập nhật</th>
                                        <th>Trạng thái</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {tickets.map((ticket) => (
                                        <tr key={ticket.id}>
                                            <td className="cell-strong">#{ticket.id}</td>
                                            <td>
                                                <div className="ticket-title-stack">
                                                    <strong>{ticket.title}</strong>
                                                </div>
                                            </td>
                                            <td>{ticket.studentName || '—'}</td>
                                            <td>{feedbackTypeLabel(ticket.feedbackType)}</td>
                                            <td>{formatDateTime(ticket.updatedAt || ticket.createdAt)}</td>
                                            <td><TicketBadge status={ticket.status} /></td>
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
                    <LoadingBlock text="Đang tải ticket..." />
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
                                    <span>Loại phản ánh</span>
                                    <strong>{feedbackTypeLabel(selectedTicket.feedbackType)}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Ngày gửi</span>
                                    <strong>{formatDateTime(selectedTicket.createdAt)}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Trạng thái</span>
                                    <strong>{statusLabelMap[selectedTicket.status] || selectedTicket.status}</strong>
                                </div>
                            </div>

                            <div className="detail-block">
                                <label>Nội dung phản ánh</label>
                                <div className="text-box">{selectedTicket.content}</div>
                            </div>

                            {selectedTicket.adminNote && (
                                <div className="detail-block">
                                    <label>Ghi chú quản trị viên</label>
                                    <div className="text-box text-box-soft">{selectedTicket.adminNote}</div>
                                </div>
                            )}
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
                                        <div key={message.id} className={`chat-bubble ${message.senderRole === 'LECTURER' ? 'outgoing' : 'incoming'}`}>
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
                                            {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                                        </select>
                                    </Field>
                                    <Field label="Ghi chú">
                                        <input className="input" value={statusNote} onChange={(event) => setStatusNote(event.target.value)} placeholder="Nhập ghi chú cập nhật..." />
                                    </Field>
                                </div>
                                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                    <button className="btn btn-primary" onClick={handleStatusUpdate} disabled={updating}>{updating ? 'Đang cập nhật...' : 'Cập nhật trạng thái'}</button>
                                </div>

                                <Field label="Phản hồi cho sinh viên">
                                    <textarea rows={4} className="input" value={replyText} onChange={(event) => setReplyText(event.target.value)} placeholder="Nhập nội dung trả lời cho sinh viên..." />
                                </Field>
                                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                    <button className="btn btn-primary" onClick={submitReply} disabled={sendingReply}>{sendingReply ? 'Đang gửi...' : 'Gửi phản hồi'}</button>
                                </div>
                            </div>
                        </div>
                    </div>
                ) : null}
            </Modal>
        </div>
    );
};

export default LecturerFeedbacks;
