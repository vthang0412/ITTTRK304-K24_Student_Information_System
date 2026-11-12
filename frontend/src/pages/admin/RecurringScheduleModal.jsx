import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { courseSectionApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { ClockIcon, PlusIcon, TrashIcon } from '../../components/Icons';
import { Field, LoadingBlock, Modal } from '../../components/UI';
import { getErrorMessage } from '../../utils/format';

const WEEKDAYS = [
  { value: 1, label: 'Thứ 2' },
  { value: 2, label: 'Thứ 3' },
  { value: 3, label: 'Thứ 4' },
  { value: 4, label: 'Thứ 5' },
  { value: 5, label: 'Thứ 6' },
  { value: 6, label: 'Thứ 7' },
  { value: 7, label: 'Chủ Nhật' },
];

const emptySession = () => ({
  dayOfWeek: 1,
  startPeriod: 1,
  endPeriod: 2,
  startTime: '07:30',
  endTime: '09:30',
  classroom: '',
});

const addHours = (time, hours) => {
  const [hour, minute] = time.split(':').map(Number);
  const totalMinutes = hour * 60 + minute + hours * 60;
  return `${String(Math.floor(totalMinutes / 60) % 24).padStart(2, '0')}:${String(totalMinutes % 60).padStart(2, '0')}`;
};

const defaultStartDate = (section) => {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  return section.semester?.startDate && section.semester.startDate > today
    ? section.semester.startDate
    : today;
};

const RecurringScheduleModal = ({ section, onClose, onSaved }) => {
  const toast = useToast();
  const [sessions, setSessions] = useState([]);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [legacyScheduleActive, setLegacyScheduleActive] = useState(Boolean(section.schedule));
  const hasSessions = sessions.length > 0;
  const hasSchedule = hasSessions || legacyScheduleActive;

  useEffect(() => {
    let active = true;
    courseSectionApi.getRecurringSchedule(section.id)
      .then(({ data }) => {
        if (!active) return;
        const values = Array.isArray(data) ? data : [];
        setSessions(values.map((item) => ({
          dayOfWeek: item.dayOfWeek,
          startPeriod: item.startPeriod,
          endPeriod: item.endPeriod,
          startTime: String(item.startTime || '07:30').slice(0, 5),
          endTime: String(item.endTime || '09:30').slice(0, 5),
          classroom: item.classroom || '',
        })));
        const initialStart = values[0]?.startDate || defaultStartDate(section);
        setStartDate(initialStart);
        setEndDate(values[0]?.endDate || section.semester?.endDate || initialStart);
      })
      .catch((error) => {
        if (active) toast.error('Không tải được lịch học', getErrorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [section, toast]);

  const periodSummary = useMemo(() => sessions.map((session) => {
    const weekday = WEEKDAYS.find((day) => day.value === Number(session.dayOfWeek))?.label || '';
    return `${weekday}, tiết ${session.startPeriod}–${session.endPeriod}, ${session.startTime}–${session.endTime}, ${session.classroom}`;
  }), [sessions]);

  const changeSession = (index, key, value) => {
    setSessions((current) => current.map((session, sessionIndex) => {
      if (sessionIndex !== index) return session;
      const next = { ...session, [key]: value };
      if (key === 'startTime') next.endTime = addHours(value, 2);
      if (key === 'startPeriod' && Number(next.endPeriod) <= Number(value)) {
        next.endPeriod = Number(value) + 1;
      }
      return next;
    }));
  };

  const beginEditing = () => {
    if (!hasSessions) {
      const start = defaultStartDate(section);
      setStartDate(start);
      setEndDate(section.semester?.endDate && section.semester.endDate >= start
        ? section.semester.endDate : start);
      setSessions([emptySession()]);
    } else {
      const minimum = defaultStartDate(section);
      const start = startDate > minimum ? startDate : minimum;
      setStartDate(start);
      if (endDate < start) {
        setEndDate(section.semester?.endDate && section.semester.endDate >= start
          ? section.semester.endDate : start);
      }
    }
    setEditing(true);
  };

  const save = async (event) => {
    event.preventDefault();
    if (!startDate || !endDate || startDate > endDate) {
      toast.error('Khoảng ngày không hợp lệ', 'Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.');
      return;
    }
    if (sessions.some((session) => !session.classroom.trim())) {
      toast.error('Thiếu phòng học', 'Vui lòng nhập phòng cho từng buổi học.');
      return;
    }
    const payload = {
      startDate,
      endDate,
      sessions: sessions.map((session) => ({
        ...session,
        dayOfWeek: Number(session.dayOfWeek),
        startPeriod: Number(session.startPeriod),
        endPeriod: Number(session.endPeriod),
        classroom: session.classroom.trim(),
      })),
    };

    setSaving(true);
    try {
      await courseSectionApi.checkRecurringSchedule(section.id, payload);
      const { data } = await courseSectionApi.saveRecurringSchedule(section.id, payload);
      setSessions(data.map((item) => ({
        dayOfWeek: item.dayOfWeek,
        startPeriod: item.startPeriod,
        endPeriod: item.endPeriod,
        startTime: String(item.startTime || '').slice(0, 5),
        endTime: String(item.endTime || '').slice(0, 5),
        classroom: item.classroom || '',
      })));
      setLegacyScheduleActive(false);
      setEditing(false);
      toast.success('Đã lưu lịch học', `Lịch ${section.sectionCode} sẽ lặp theo tuần trong khoảng ngày đã chọn.`);
      onSaved?.();
    } catch (error) {
      toast.error('Không thể lưu lịch học', getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const removeSchedule = async () => {
    const result = await Swal.fire({
      title: 'Xóa lịch học định kỳ?',
      text: 'Các buổi từ ngày mai trở đi sẽ bị hủy. Lịch sử các buổi trước đó vẫn được giữ lại.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Xóa lịch',
      cancelButtonText: 'Giữ lại',
    });
    if (!result.isConfirmed) return;

    try {
      await courseSectionApi.deleteRecurringSchedule(section.id);
      setSessions([]);
      setLegacyScheduleActive(false);
      setEditing(false);
      toast.success('Đã xóa lịch học sắp tới', 'Các buổi học đã qua vẫn được lưu trong lịch sử.');
      onSaved?.();
    } catch (error) {
      toast.error('Không thể xóa lịch học', getErrorMessage(error));
    }
  };

  return (
    <Modal
      open
      onClose={saving ? () => {} : onClose}
      title={editing ? 'Thiết lập lịch học định kỳ' : 'Lịch học lớp học phần'}
      subtitle={`${section.sectionCode} · ${section.course?.courseName || section.course?.courseCode || ''}`}
      size="lg"
      footer={(
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>Đóng</button>
          {!editing && hasSchedule && (
            <button type="button" className="btn btn-danger" onClick={removeSchedule}>Xóa lịch học</button>
          )}
          {!editing && (
            <button type="button" className="btn btn-primary" onClick={beginEditing}>
              {hasSessions ? 'Chỉnh sửa lịch' : 'Thiết lập lịch'}
            </button>
          )}
          {editing && (
            <button type="submit" form="recurring-schedule-form" className="btn btn-primary" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu lịch học'}
            </button>
          )}
        </>
      )}
    >
      {loading ? <LoadingBlock text="Đang tải lịch học..." /> : (
        <form id="recurring-schedule-form" onSubmit={save}>
          {!editing ? (
            hasSessions ? (
              <>
                <div className="schedule-line"><ClockIcon size={15} />{startDate} – {endDate}</div>
                <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
                  {periodSummary.map((summary, index) => <div className="card-body" key={`${summary}-${index}`}>{summary}</div>)}
                </div>
              </>
            ) : legacyScheduleActive ? (
              <p className="muted">
                Lớp đang có lịch dạng cũ: <strong>{section.schedule}</strong>.
                Bạn có thể thiết lập lịch định kỳ để quản lý rõ thứ, tiết và giờ; hoặc xóa các buổi từ ngày mai trở đi.
              </p>
            ) : (
              <p className="muted">Lớp này chưa có lịch định kỳ. Lịch sẽ chỉ phát sinh vào thứ đã chọn trong khoảng ngày.</p>
            )
          ) : (
            <>
              <div className="form-grid">
                <Field label="Ngày bắt đầu" required>
                  <input className="input" type="date" min={defaultStartDate(section)} value={startDate}
                    onChange={(event) => setStartDate(event.target.value)} />
                </Field>
                <Field label="Ngày kết thúc" required>
                  <input className="input" type="date" min={startDate || defaultStartDate(section)} max={section.semester?.endDate}
                    value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                </Field>
              </div>
              <p className="field-hint" style={{ margin: '8px 0 16px' }}>
                Mỗi buổi lặp vào đúng thứ trong khoảng ngày. Tiết kết thúc phải sau tiết bắt đầu.
                Khung giờ bên dưới là giờ thực tế dùng để tránh trùng lịch; mặc định 2 giờ và có thể chỉnh thành 3 giờ hoặc thời lượng khác.
              </p>
              <div style={{ display: 'grid', gap: 12 }}>
                {sessions.map((session, index) => (
                  <div className="card-body" key={`session-${index}`}>
                    <div className="form-grid">
                      <Field label="Thứ trong tuần" required>
                        <select className="select" value={session.dayOfWeek}
                          onChange={(event) => changeSession(index, 'dayOfWeek', Number(event.target.value))}>
                          {WEEKDAYS.map((day) => <option key={day.value} value={day.value}>{day.label}</option>)}
                        </select>
                      </Field>
                      <Field label="Tiết bắt đầu" required>
                        <input className="input" type="number" min="1" value={session.startPeriod}
                          onChange={(event) => changeSession(index, 'startPeriod', event.target.value)} />
                      </Field>
                      <Field label="Tiết kết thúc" required>
                        <input className="input" type="number" min={Number(session.startPeriod || 0) + 1} value={session.endPeriod}
                          onChange={(event) => changeSession(index, 'endPeriod', event.target.value)} />
                      </Field>
                      <Field label="Phòng học" required>
                        <input className="input" maxLength="100" value={session.classroom}
                          onChange={(event) => changeSession(index, 'classroom', event.target.value)}
                          placeholder="VD: A101" />
                      </Field>
                      <Field label="Giờ bắt đầu" required>
                        <input className="input" type="time" value={session.startTime}
                          onChange={(event) => changeSession(index, 'startTime', event.target.value)} />
                      </Field>
                      <Field label="Giờ kết thúc" required>
                        <input className="input" type="time" value={session.endTime}
                          onChange={(event) => changeSession(index, 'endTime', event.target.value)} />
                      </Field>
                      <div className="field" style={{ alignSelf: 'end' }}>
                        <button type="button" className="btn btn-danger btn-sm" disabled={sessions.length <= 1}
                          onClick={() => setSessions((current) => current.filter((_, sessionIndex) => sessionIndex !== index))}>
                          <TrashIcon size={14} /> Xóa buổi
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <button type="button" className="btn btn-secondary btn-sm" style={{ marginTop: 14 }}
                onClick={() => setSessions((current) => [...current, emptySession()])}>
                <PlusIcon size={14} /> Thêm buổi học trong tuần
              </button>
            </>
          )}
        </form>
      )}
    </Modal>
  );
};

export default RecurringScheduleModal;
