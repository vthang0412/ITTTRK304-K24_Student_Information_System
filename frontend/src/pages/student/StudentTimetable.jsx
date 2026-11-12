import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { enrollmentApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { CalendarIcon, ClockIcon, LayersIcon, SearchIcon } from '../../components/Icons';
import { EmptyState, LoadingBlock, PageHeader } from '../../components/UI';

const DAYS = [
    { key: 'MON', label: 'Thứ 2', index: 1 },
    { key: 'TUE', label: 'Thứ 3', index: 2 },
    { key: 'WED', label: 'Thứ 4', index: 3 },
    { key: 'THU', label: 'Thứ 5', index: 4 },
    { key: 'FRI', label: 'Thứ 6', index: 5 },
    { key: 'SAT', label: 'Thứ 7', index: 6 },
    { key: 'SUN', label: 'Chủ nhật', index: 0 },
];

const COLOR_PALETTE = ['#2563eb', '#14b8a6', '#f59e0b', '#8b5cf6', '#ef4444', '#10b981', '#0ea5e9'];

const toMinutes = (time) => {
    if (!time) return 0;
    const [hour, minute] = time.split(':').map(Number);
    if (Number.isNaN(hour) || Number.isNaN(minute)) return 0;
    return hour * 60 + minute;
};

const parseDay = (raw = '') => {
    const value = String(raw).trim().toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    if (!value) return null;

    if (value.includes('cn') || value.includes('chu nhat')) return 0;
    if (value.includes('thu 2') || value.includes('thu2') || value.includes('thu hai')) return 1;
    if (value.includes('thu 3') || value.includes('thu3') || value.includes('thu ba')) return 2;
    if (value.includes('thu 4') || value.includes('thu4') || value.includes('thu tu')) return 3;
    if (value.includes('thu 5') || value.includes('thu5') || value.includes('thu nam')) return 4;
    if (value.includes('thu 6') || value.includes('thu6') || value.includes('thu sau')) return 5;
    if (value.includes('thu 7') || value.includes('thu7') || value.includes('thu bay') || value.includes('thu bảy')) return 6;

    const numeric = value.match(/thu\s*(\d)/);
    if (numeric) {
        const num = Number(numeric[1]);
        if (num >= 2 && num <= 7) return num - 1;
    }

    return null;
};

const parseScheduleLine = (raw = '') => {
    const clean = String(raw || '').trim();
    if (!clean) return [];

    return clean
        .split(';')
        .map((segment) => segment.trim())
        .filter(Boolean)
        .flatMap((segment) => {
            const timeMatch = segment.match(/(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})/i);
            const day = parseDay(segment);
            if (!timeMatch || day === null) {
                return [];
            }

            const start = toMinutes(timeMatch[1]);
            const end = toMinutes(timeMatch[2]);
            if (end <= start) return [];

            const roomText = segment.slice(timeMatch.index + timeMatch[0].length).replace(/^\s*[,\-–]*\s*/, '').trim();

            return [{
                raw: segment,
                day,
                start,
                end,
                room: roomText || 'Phòng chưa cập nhật',
                label: `${timeMatch[1]} - ${timeMatch[2]}`,
            }];
        });
};

const formatRange = (start, end) => {
    if (!start || !end) return 'Chưa cập nhật';
    const format = (value) => {
        const hours = Math.floor(value / 60).toString().padStart(2, '0');
        const minutes = (value % 60).toString().padStart(2, '0');
        return `${hours}:${minutes}`;
    };
    return `${format(start)} - ${format(end)}`;
};

const StudentTimetable = () => {
    const toast = useToast();
    const [enrollments, setEnrollments] = useState([]);
    const [loading, setLoading] = useState(true);

    const load = async () => {
        try {
            const res = await enrollmentApi.getMyEnrollments();
            setEnrollments(res.data || []);
        } catch {
            toast.error('Không tải được thời khóa biểu', 'Vui lòng thử lại sau.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const activeEnrollments = useMemo(
        () => enrollments.filter((e) => e.status === 'ENROLLED' && e.courseSection),
        [enrollments]
    );

    const classCards = useMemo(() => {
        const items = [];
        activeEnrollments.forEach((e) => {
            const section = e.courseSection;
            const slots = parseScheduleLine(section?.schedule);
            slots.forEach((slot, index) => {
                if (slot.day === null) return;
                items.push({
                    key: `${e.id}-${section?.sectionCode}-${slot.day}-${index}`,
                    id: e.id,
                    courseName: section?.course?.courseName || 'Môn học',
                    courseCode: section?.course?.courseCode || '—',
                    sectionCode: section?.sectionCode || '—',
                    lecturer: section?.lecturer?.fullName || 'Chưa phân công',
                    room: slot.room,
                    day: slot.day,
                    start: slot.start,
                    end: slot.end,
                    label: `${section?.sectionCode || 'Lớp'} • ${section?.course?.courseCode || ''}`,
                    raw: slot.raw,
                });
            });
        });
        return items;
    }, [activeEnrollments]);

    const conflictSet = useMemo(() => {
        const conflicts = new Set();
        for (let i = 0; i < classCards.length; i += 1) {
            for (let j = i + 1; j < classCards.length; j += 1) {
                const a = classCards[i];
                const b = classCards[j];
                if (a.day !== b.day) continue;
                const overlaps = a.start < b.end && b.start < a.end;
                if (overlaps) {
                    conflicts.add(a.key);
                    conflicts.add(b.key);
                }
            }
        }
        return conflicts;
    }, [classCards]);

    const groupedByDay = useMemo(
        () => DAYS.map((day) => classCards.filter((card) => card.day === day.index).sort((a, b) => a.start - b.start || a.end - b.end)),
        [classCards]
    );

    const totalCredits = useMemo(
        () => activeEnrollments.reduce((sum, e) => sum + Number(e.courseSection?.course?.credits || 0), 0),
        [activeEnrollments]
    );

    const nextClass = useMemo(() => {
        if (!classCards.length) return null;

        const now = new Date();
        const todayIndex = now.getDay() === 0 ? 6 : now.getDay() - 1;
        const currentMinute = now.getHours() * 60 + now.getMinutes();

        const upcoming = classCards
            .filter((card) => {
                const isFutureWeekday = card.day > todayIndex;
                const isToday = card.day === todayIndex;
                const hasNotEnded = card.end > currentMinute;
                return isFutureWeekday || (isToday && hasNotEnded);
            })
            .filter((card) => {
                if (!Number.isFinite(card.start) || !Number.isFinite(card.end)) return false;
                return true;
            })
            .sort((a, b) => {
                const aValue = (a.day + 1) * 1440 + a.start;
                const bValue = (b.day + 1) * 1440 + b.start;
                return aValue - bValue;
            })[0];

        return upcoming || null;
    }, [classCards]);

    if (loading) {
        return (
            <>
                <PageHeader title="Thời khóa biểu" subtitle="Đang tổng hợp lịch học từ các lớp bạn đã đăng ký..." />
                <div className="card"><LoadingBlock /></div>
            </>
        );
    }

    return (
        <>
            <PageHeader
                title="Thời khóa biểu"
                subtitle="Theo dõi lịch học theo tuần, phát hiện xung đột và quản lý thời gian học hiệu quả hơn."
                actions={(
                    <div className="page-head-actions">
                        <Link to="/student/enrollments" className="btn btn-secondary">Học phần đã đăng ký</Link>
                        <Link to="/student/courses" className="btn btn-primary">Đăng ký thêm</Link>
                    </div>
                )}
            />

            <div className="timetable-summary">
                <div className="stat-card">
                    <div className="stat-icon tone-blue"><CalendarIcon /></div>
                    <div className="stat-info"><small>Tổng số lớp</small><strong>{activeEnrollments.length}</strong></div>
                </div>
                <div className="stat-card">
                    <div className="stat-icon tone-green"><LayersIcon /></div>
                    <div className="stat-info"><small>Tín chỉ</small><strong>{totalCredits}</strong></div>
                </div>
                <div className="stat-card">
                    <div className="stat-icon tone-amber"><ClockIcon /></div>
                    <div className="stat-info"><small>Lượt trùng lịch</small><strong>{conflictSet.size ? `${conflictSet.size / 2}` : 0}</strong></div>
                </div>
                <div className="stat-card">
                    <div className="stat-icon tone-indigo"><SearchIcon /></div>
                    <div className="stat-info"><small>Buổi tiếp theo</small><strong>{nextClass ? nextClass.sectionCode : '—'}</strong></div>
                </div>
            </div>

            {conflictSet.size > 0 && (
                <div className="alert alert-warning" style={{ marginBottom: 18 }}>
                    <ClockIcon size={17} />
                    <div>
                        <strong>Phát hiện lịch chồng chéo</strong>
                        <div style={{ marginTop: 4 }}>Một số học phần đang trùng thời gian trong cùng ngày. Hãy xem lại lịch tuần để tránh xếp lớp không hiệu quả.</div>
                    </div>
                </div>
            )}

            <div className="card timetable-card">
                <div className="card-head">
                    <div>
                        <h2>Lịch học theo tuần</h2>
                        <p>Thời khóa biểu được tự động suy ra từ dữ liệu học phần đã đăng ký.</p>
                    </div>
                    <div className="timetable-legend">
                        <span className="legend-item"><span className="legend-dot primary" />Mình đã đăng ký</span>
                        <span className="legend-item"><span className="legend-dot warning" />Có xung đột</span>
                    </div>
                </div>
                <div className="card-body">
                    {classCards.length === 0 ? (
                        <EmptyState
                            icon={<CalendarIcon />}
                            title="Chưa có lịch học"
                            description="Bạn chưa đăng ký học phần nào có thời gian học khả dụng để hiển thị thời khóa biểu."
                            action={<Link to="/student/courses" className="btn btn-primary">Đăng ký học phần</Link>}
                        />
                    ) : (
                        <div className="timetable-grid">
                            {DAYS.map((day) => {
                                const dayItems = groupedByDay[day.index === 0 ? 6 : day.index - 1];
                                return (
                                    <div key={day.key} className="timetable-day">
                                        <div className="timetable-day-header">{day.label}</div>
                                        {dayItems && dayItems.length > 0 ? (
                                            <div className="timetable-day-items">
                                                {dayItems.map((item) => (
                                                    <div
                                                        key={item.key}
                                                        className={`timetable-item ${conflictSet.has(item.key) ? 'has-conflict' : ''}`}
                                                        style={{ borderLeftColor: COLOR_PALETTE[(item.sectionCode?.length || 0) % COLOR_PALETTE.length] }}
                                                    >
                                                        <div className="timetable-item-top">
                                                            <strong>{item.courseName}</strong>
                                                            <span>{item.sectionCode}</span>
                                                        </div>
                                                        <div className="timetable-item-meta">{formatRange(item.start, item.end)}</div>
                                                        <div className="timetable-item-meta">{item.room}</div>
                                                        <div className="timetable-item-meta muted-text">GV: {item.lecturer}</div>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="timetable-empty">Không có lịch</div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </>
    );
};

export default StudentTimetable;
