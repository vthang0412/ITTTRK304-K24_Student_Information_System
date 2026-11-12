import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { enrollmentApi, feedbackApi, lecturerApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { Field, LoadingBlock, Modal, PageHeader } from '../../components/UI';
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

const StudentFeedbacks = () => {
    const toast = useToast();
    const [tickets, setTickets] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedTicket, setSelectedTicket] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [replyText, setReplyText] = useState('');
    const [sendingReply, setSendingReply] = useState(false);
    const [createOpen, setCreateOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [lecturers, setLecturers] = useState([]);
    const [myEnrollments, setMyEnrollments] = useState([]);
    const [form, setForm] = useState({
        title: '',
        feedbackType: 'OTHER',
        content: '',
        courseSectionId: '',
        lecturerId: '',
    });

    const loadLecturersAndEnrollments = async () => {
        try {
            const [lecRes, enrRes] = await Promise.all([
                lecturerApi.getAll(),
                enrollmentApi.getMyEnrollments(),
            ]);
            setLecturers(lecRes.data || []);
            setMyEnrollments((enrRes.data || []).filter((e) => e.status === 'ENROLLED'));
        } catch {
            // Không block nếu lỗi phụ
        }
    };

    const loadTickets = async () => {
        setLoading(true);
        try {
            const response = await feedbackApi.getMy();
            setTickets(response.data || []);
        } catch (error) {
            toast.error('Không thể tải phản ánh', getErrorMessage(error));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadTickets();
        loadLecturersAndEnrollments();
    }, []);

    const openTicket = async (ticketId) => {
        setDetailLoading(true);
        try {
            const response = await feedbackApi.getById(ticketId);
            setSelectedTicket(response.data);
        } catch (error) {
            toast.error('Không thể mở chi tiết ticket', getErrorMessage(error));
        } finally {
            setDetailLoading(false);
        }
    };

    const submitCreate = async () => {
        if (!form.title.trim() || !form.content.trim()) {
            toast.error('Thiếu dữ liệu', 'Vui lòng nhập tiêu đề và nội dung phản ánh.');
            return;
        }

        const selectedLecturer = lecturers.find((l) => String(l.id) === String(form.lecturerId));
        const confirmText = selectedLecturer
            ? `Phản ánh sẽ được gửi trực tiếp đến Giảng viên ${selectedLecturer.fullName} để xử lý.`
            : 'Ticket sẽ được gửi tới nhà trường và có thể được phân công cho giảng viên phụ trách.';

        const result = await Swal.fire({
            title: 'Xác nhận gửi phản ánh?',
            text: confirmText,
            icon: 'question',
            showCancelButton: true,
            confirmButtonText: 'Gửi phản ánh',
            cancelButtonText: 'Hủy',
            reverseButtons: true,
        });
        if (!result.isConfirmed) return;

        setSaving(true);
        try {
            await feedbackApi.create({
                title: form.title.trim(),
                feedbackType: form.feedbackType,
                content: form.content.trim(),
                courseSectionId: form.courseSectionId ? Number(form.courseSectionId) : null,
                lecturerId: form.lecturerId ? Number(form.lecturerId) : null,
            });
            toast.success('Gửi phản ánh thành công', selectedLecturer
                ? `Ticket đã được chuyển trực tiếp cho GV ${selectedLecturer.fullName}.`
                : 'Ticket đã được tạo và đang chờ tiếp nhận.');
            setCreateOpen(false);
            setForm({ title: '', feedbackType: 'OTHER', content: '', courseSectionId: '', lecturerId: '' });
            loadTickets();
        } catch (error) {
            toast.error('Gửi phản ánh thất bại', getErrorMessage(error));
        } finally {
            setSaving(false);
        }
    };

    const submitReply = async () => {
        if (!selectedTicket || !replyText.trim()) {
            toast.error('Thiếu nội dung', 'Vui lòng nhập tin nhắn phản hồi.');
            return;
        }

        setSendingReply(true);
        try {
            const response = await feedbackApi.addMessage(selectedTicket.id, { message: replyText.trim() });
            setSelectedTicket((prev) => prev ? ({ ...prev, status: prev.status === 'PENDING' ? 'RESPONDED' : prev.status, messages: [...(prev.messages || []), response.data] }) : prev);
            setReplyText('');
            toast.success('Gửi tin nhắn thành công', 'Lịch sử trao đổi đã được cập nhật.');
            loadTickets();
        } catch (error) {
            toast.error('Không thể gửi tin nhắn', getErrorMessage(error));
        } finally {
            setSendingReply(false);
        }
    };

    const summary = useMemo(() => ({
        total: tickets.length,
        pending: tickets.filter((item) => item.status === 'PENDING').length,
        inProgress: tickets.filter((item) => item.status === 'IN_PROGRESS').length,
        responded: tickets.filter((item) => item.status === 'RESPONDED' || item.status === 'RESOLVED').length,
    }), [tickets]);

    return (
        <div className="feedback-page">
            <div className="feedback-hero">
                <div>
                    <span className="eyebrow">Support center</span>
                    <h1>Phản ánh và hỗ trợ</h1>
                    <p>Theo dõi và quản lý các yêu cầu hỗ trợ từ nhà trường.</p>
                </div>
                <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>Tạo phản ánh mới</button>
            </div>

            <div className="feedback-stats">
                <div className="feedback-stat stat-blue">
                    <div className="feedback-stat-icon"><span>#</span></div>
                    <div>
                        <small>Tổng số phản ánh</small>
                        <strong>{summary.total}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-amber">
                    <div className="feedback-stat-icon"><span>⏳</span></div>
                    <div>
                        <small>Chờ tiếp nhận</small>
                        <strong>{summary.pending}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-indigo">
                    <div className="feedback-stat-icon"><span>⚙</span></div>
                    <div>
                        <small>Đang xử lý</small>
                        <strong>{summary.inProgress}</strong>
                    </div>
                </div>
                <div className="feedback-stat stat-green">
                    <div className="feedback-stat-icon"><span>✓</span></div>
                    <div>
                        <small>Đã hoàn thành</small>
                        <strong>{summary.responded}</strong>
                    </div>
                </div>
            </div>

            <div className="card feedback-panel">
                <div className="card-head">
                    <div>
                        <h2>Danh sách phản ánh</h2>
                        <p>Quản lý các yêu cầu hỗ trợ của bạn.</p>
                    </div>
                </div>
                <div className="card-body">
                    {loading ? (
                        <div className="empty-state"><LoadingBlock text="Đang tải dữ liệu phản ánh..." /></div>
                    ) : tickets.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">✦</div>
                            <h3>Chưa có phản ánh nào</h3>
                            <p>Gửi yêu cầu hỗ trợ mới để nhận được phản hồi từ nhà trường.</p>
                        </div>
                    ) : (
                        <div className="feedback-table-wrap">
                            <table className="data-table feedback-table">
                                <thead>
                                    <tr>
                                        <th>Mã</th>
                                        <th>Tiêu đề</th>
                                        <th>Người phụ trách</th>
                                        <th>Ngày gửi</th>
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
                                                    <small>{feedbackTypeLabel(ticket.feedbackType)}</small>
                                                </div>
                                            </td>
                                            <td>{ticket.assignedLecturerName || 'Chưa phân công'}</td>
                                            <td>{formatDateTime(ticket.updatedAt || ticket.createdAt)}</td>
                                            <td><TicketBadge status={ticket.status} /></td>
                                            <td>
                                                <button className="btn btn-secondary btn-sm" onClick={() => openTicket(ticket.id)}>Xem</button>
                                            </td>
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
                                    <span>Loại phản ánh</span>
                                    <strong>{feedbackTypeLabel(selectedTicket.feedbackType)}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Người phụ trách</span>
                                    <strong>{selectedTicket.assignedLecturerName || 'Chưa phân công'}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Ngày gửi</span>
                                    <strong>{formatDateTime(selectedTicket.createdAt)}</strong>
                                </div>
                                <div className="meta-card">
                                    <span>Trạng thái hiện tại</span>
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
                                    const isCurrent = selectedTicket.status === step;
                                    const isPast = ['PENDING', 'IN_PROGRESS', 'RESPONDED', 'RESOLVED'].indexOf(selectedTicket.status) >= index;
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
                                        <div key={message.id} className={`chat-bubble ${message.senderRole === 'STUDENT' ? 'incoming' : 'outgoing'}`}>
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
                                <Field label="Phản hồi cho nhà trường">
                                    <textarea className="input" rows={4} value={replyText} onChange={(event) => setReplyText(event.target.value)} placeholder="Nhập nội dung phản hồi của bạn..." />
                                </Field>
                                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                    <button className="btn btn-primary" onClick={submitReply} disabled={sendingReply}>{sendingReply ? 'Đang gửi...' : 'Gửi tin nhắn'}</button>
                                </div>
                            </div>
                        </div>
                    </div>
                ) : null}
            </Modal>

            <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Gửi phản ánh mới" subtitle="Mô tả vấn đề bạn đang gặp phải." size="md" footer={(
                <>
                    <button className="btn btn-secondary" onClick={() => setCreateOpen(false)} disabled={saving}>Hủy</button>
                    <button className="btn btn-primary" onClick={submitCreate} disabled={saving}>{saving ? 'Đang gửi...' : 'Gửi phản ánh'}</button>
                </>
            )}>
                <div className="feedback-form">
                    <Field label="Tiêu đề" required>
                        <input className="input" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="Ví dụ: Tôi cần hỗ trợ về điểm số học phần ..." />
                    </Field>
                    <Field label="Loại phản ánh" required>
                        <select className="input" value={form.feedbackType} onChange={(event) => setForm({ ...form, feedbackType: event.target.value })}>
                            {FEEDBACK_TYPE_OPTIONS.map((item) => (
                                <option key={item.value} value={item.value}>{item.label}</option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Lớp học phần liên quan (Tùy chọn)">
                        <select
                            className="input"
                            value={form.courseSectionId}
                            onChange={(event) => {
                                const sectionId = event.target.value;
                                const matched = myEnrollments.find((e) => String(e.courseSection?.id) === String(sectionId));
                                setForm((prev) => ({
                                    ...prev,
                                    courseSectionId: sectionId,
                                    // Tự động gán Giảng viên của lớp nếu có
                                    lecturerId: matched?.courseSection?.lecturer?.id ? String(matched.courseSection.lecturer.id) : prev.lecturerId,
                                }));
                            }}
                        >
                            <option value="">-- Không thuộc lớp cụ thể --</option>
                            {myEnrollments.map((e) => {
                                const sec = e.courseSection;
                                return (
                                    <option key={sec?.id} value={sec?.id}>
                                        {sec?.sectionCode} - {sec?.course?.courseName} ({sec?.lecturer?.fullName ? `GV: ${sec.lecturer.fullName}` : 'Chưa xếp GV'})
                                    </option>
                                );
                            })}
                        </select>
                    </Field>
                    <Field label="Chỉ định giảng viên xử lý trực tiếp">
                        <select
                            className="input"
                            value={form.lecturerId}
                            onChange={(event) => setForm({ ...form, lecturerId: event.target.value })}
                        >
                            <option value="">-- Gửi cho Quản trị viên điều phối (Mặc định) --</option>
                            {lecturers.map((l) => (
                                <option key={l.id} value={l.id}>
                                    {l.fullName} ({l.lecturerCode}) {l.department ? `- Khoa ${l.department}` : ''}
                                </option>
                            ))}
                        </select>
                        <small style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4, display: 'block' }}>
                            Chọn giảng viên để ticket được gửi thẳng cho giảng viên đó xử lý mà không cần chờ Admin phân công.
                        </small>
                    </Field>
                    <Field label="Nội dung phản ánh" required>
                        <textarea className="input" rows={7} value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} placeholder="Mô tả chi tiết vấn đề, thời gian xảy ra và mong muốn hỗ trợ..." />
                    </Field>
                </div>
            </Modal>
        </div>
    );
};

export default StudentFeedbacks;
