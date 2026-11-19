import React, { useEffect, useState } from 'react';
import {
  aiStudyPlanApi,
} from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  Alert, ConfirmDialog, EmptyState, Field, LoadingBlock, Modal, PageHeader, Spinner, StatCard,
} from '../../components/UI';
import {
  BookIcon, CalendarIcon, CheckIcon, PlusIcon, TrashIcon,
} from '../../components/Icons';
import {
  formatDate, getErrorMessage, toDateInput,
} from '../../utils/format';
import './ai-pages.css';

const unwrap = (res) => res?.data?.data ?? res?.data ?? null;

const addDays = (date, days) => {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
};

const defaultForm = () => ({
  title: 'Kế hoạch học tập 7 ngày',
  goal: '',
  startDate: toDateInput(new Date()),
  endDate: toDateInput(addDays(new Date(), 6)),
  targetHoursPerDay: '2',
});

const AIStudyPlanner = () => {
  const toast = useToast();
  const [plans, setPlans] = useState([]);
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [busyItemId, setBusyItemId] = useState(null);
  const [form, setForm] = useState(defaultForm());

  const loadDetail = async (id) => {
    setDetailLoading(true);
    try {
      const res = await aiStudyPlanApi.getDetail(id);
      setDetail(unwrap(res));
    } catch (err) {
      toast.error('Không tải được chi tiết kế hoạch', getErrorMessage(err));
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const loadPlans = async () => {
    setLoading(true);
    try {
      const res = await aiStudyPlanApi.getAll();
      const list = unwrap(res) || [];
      setPlans(list);
      if (list.length > 0) {
        setDetail(null);
        await loadDetail(list[0].id);
      } else {
        setDetail(null);
      }
    } catch (err) {
      toast.error('Không tải được kế hoạch học tập', getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadPlans(); }, []);

  const handleSelect = (id) => {
    setDetail(null);
    loadDetail(id);
  };

  const handleChange = (key) => (event) => {
    setForm((prev) => ({ ...prev, [key]: event.target.value }));
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setCreating(true);
    try {
      const payload = {
        title: form.title.trim(),
        goal: form.goal || undefined,
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
        targetHoursPerDay: form.targetHoursPerDay === '' ? undefined : Number(form.targetHoursPerDay),
      };
      const res = await aiStudyPlanApi.create(payload);
      const created = unwrap(res);
      setPlans((prev) => [created, ...prev]);
      setDetail(created);
      setCreateOpen(false);
      setForm(defaultForm());
      toast.success('Đã tạo kế hoạch học tập');
    } catch (err) {
      toast.error('Không thể tạo kế hoạch', getErrorMessage(err));
    } finally {
      setCreating(false);
    }
  };

  const handlePlanStatus = async (status) => {
    if (!detail) return;
    try {
      const res = await aiStudyPlanApi.update(detail.id, { status });
      const updated = unwrap(res);
      setDetail((prev) => ({ ...prev, ...updated }));
      setPlans((prev) => prev.map((p) => (p.id === detail.id ? { ...p, ...updated } : p)));
      toast.success('Đã cập nhật trạng thái kế hoạch');
    } catch (err) {
      toast.error('Không thể cập nhật kế hoạch', getErrorMessage(err));
    }
  };

  const handleItemStatus = async (item, status) => {
    if (!detail || item.status === status) return;
    setBusyItemId(item.id);
    try {
      const res = await aiStudyPlanApi.updateItem(detail.id, item.id, { status });
      const updatedItem = unwrap(res);
      setDetail((prev) => {
        const items = (prev.items || []).map((i) => (i.id === item.id ? { ...i, ...updatedItem } : i));
        const completedTasks = items.filter((i) => i.status === 'COMPLETED').length;
        return {
          ...prev,
          items,
          completedTasks,
          completionRate: items.length ? (completedTasks / items.length) * 100 : 0,
        };
      });
    } catch (err) {
      toast.error('Không thể cập nhật nhiệm vụ', getErrorMessage(err));
    } finally {
      setBusyItemId(null);
    }
  };

  const handleDelete = async () => {
    if (!deletingId) return;
    try {
      await aiStudyPlanApi.remove(deletingId);
      const remaining = plans.filter((p) => p.id !== deletingId);
      setPlans(remaining);
      if (detail?.id === deletingId) {
        setDetail(remaining.length ? { ...remaining[0] } : null);
      }
      toast.success('Đã xóa kế hoạch học tập');
    } catch (err) {
      toast.error('Không thể xóa kế hoạch', getErrorMessage(err));
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) return <LoadingBlock text="Đang tải kế hoạch học tập..." />;

  return (
    <>
      <PageHeader
        title="Kế hoạch học tập AI"
        subtitle="Lập lịch ôn tập theo tuần, theo dõi tiến độ và đánh dấu nhiệm vụ hoàn thành."
        actions={(
          <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <PlusIcon /> Tạo kế hoạch
          </button>
        )}
      />

      {plans.length === 0 ? (
        <EmptyState
          icon={<CalendarIcon />}
          title="Chưa có kế hoạch học tập"
          description="Tạo kế hoạch đầu tiên để AI gợi ý lịch học phù hợp với thời gian của bạn."
          action={(
            <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
              <PlusIcon /> Tạo kế hoạch ngay
            </button>
          )}
        />
      ) : (
        <div className="ai-page-grid ai-grid-2">
          <div className="ai-panel">
            <div className="ai-panel-head">
              <div>
                <div className="ai-panel-title">Danh sách kế hoạch</div>
                <div className="ai-panel-sub">{plans.length} kế hoạch đã được lưu.</div>
              </div>
            </div>
            <div className="ai-recommend-list">
              {plans.map((plan) => (
                <div
                  className={`ai-plan-card ${detail?.id === plan.id ? 'active' : ''}`}
                  key={plan.id}
                  onClick={() => handleSelect(plan.id)}
                  style={{ cursor: 'pointer' }}
                >
                  <div>
                    <div className="ai-plan-title">{plan.title || 'Kế hoạch học tập'}</div>
                    <div className="ai-plan-meta">
                      {formatDate(plan.startDate)} → {formatDate(plan.endDate)}
                      {' · '}{plan.completedTasks || 0}/{plan.totalTasks || 0} nhiệm vụ
                    </div>
                    <div className="ai-progress-track">
                      <div className="ai-progress-fill" style={{ width: `${Math.min(Number(plan.completionRate || 0), 100)}%` }} />
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span className={`badge badge-${plan.status === 'ACTIVE' ? 'green' : 'gray'}`}>
                      {plan.status || '—'}
                    </span>
                    <button
                      type="button"
                      className="ai-conv-delete-btn"
                      title="Xóa kế hoạch"
                      onClick={(event) => {
                        event.stopPropagation();
                        setDeletingId(plan.id);
                      }}
                    >
                      <TrashIcon size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="ai-panel">
            {detailLoading ? (
              <LoadingBlock text="Đang tải chi tiết kế hoạch..." />
            ) : !detail ? (
              <div className="ai-empty-inline">Chọn một kế hoạch để xem chi tiết.</div>
            ) : (
              <>
                <div className="ai-panel-head">
                  <div>
                    <div className="ai-panel-title">{detail.title || 'Kế hoạch học tập'}</div>
                    <div className="ai-panel-sub">
                      {detail.goal || 'Chưa có mục tiêu cụ thể.'}
                    </div>
                  </div>
                  <select
                    className="select"
                    value={detail.status || 'ACTIVE'}
                    onChange={(event) => handlePlanStatus(event.target.value)}
                    aria-label="Trạng thái kế hoạch"
                  >
                    <option value="ACTIVE">Đang thực hiện</option>
                    <option value="COMPLETED">Hoàn thành</option>
                    <option value="ARCHIVED">Lưu trữ</option>
                  </select>
                </div>

                <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
                  <StatCard label="Tiến độ" value={`${Math.round(Number(detail.completionRate || 0))}%`} icon={<CheckIcon />} tone="blue" />
                  <StatCard label="Nhiệm vụ" value={`${detail.completedTasks || 0}/${detail.totalTasks || 0}`} icon={<BookIcon />} tone="green" />
                  <StatCard label="Thời gian học" value={`${detail.totalStudyMinutes || 0} phút`} icon={<CalendarIcon />} tone="amber" />
                </div>

                {detail.aiGeneratedAdvice && (
                  <Alert tone="info" style={{ marginTop: 12 }}>{detail.aiGeneratedAdvice}</Alert>
                )}

                <div style={{ marginTop: 14 }}>
                  <div className="ai-panel-title" style={{ fontSize: 13, marginBottom: 4 }}>Nhiệm vụ</div>
                  {(detail.items || []).length === 0 ? (
                    <div className="ai-empty-inline">Kế hoạch này chưa có nhiệm vụ nào.</div>
                  ) : (
                    detail.items.map((item) => (
                      <div className="ai-task-row" key={item.id}>
                        <div className="ai-task-left">
                          <input
                            type="checkbox"
                            className="ai-task-check"
                            checked={item.status === 'COMPLETED'}
                            disabled={busyItemId === item.id}
                            onChange={(event) => handleItemStatus(item, event.target.checked ? 'COMPLETED' : 'PENDING')}
                          />
                          <div>
                            <div className="ai-task-title">{item.title || 'Nhiệm vụ học tập'}</div>
                            <div className="ai-task-desc">
                              {item.courseCode ? `${item.courseCode} · ` : ''}
                              {formatDate(item.studyDate)} · {item.durationMinutes || 0} phút
                            </div>
                            {item.description && <div className="ai-task-desc">{item.description}</div>}
                          </div>
                        </div>
                        <select
                          className="select"
                          value={item.status || 'PENDING'}
                          disabled={busyItemId === item.id}
                          onChange={(event) => handleItemStatus(item, event.target.value)}
                          aria-label="Trạng thái nhiệm vụ"
                        >
                          <option value="PENDING">Chưa làm</option>
                          <option value="COMPLETED">Hoàn thành</option>
                          <option value="SKIPPED">Bỏ qua</option>
                        </select>
                      </div>
                    ))
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      <Modal
        open={createOpen}
        title="Tạo kế hoạch học tập"
        subtitle="AI sẽ gợi ý lịch ôn tập dựa trên thông tin bạn cung cấp."
        onClose={() => { if (!creating) setCreateOpen(false); }}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setCreateOpen(false)} disabled={creating}>Hủy</button>
            <button type="submit" form="ai-plan-create-form" className="btn btn-primary" disabled={creating}>
              {creating ? <><Spinner /> Đang tạo...</> : 'Tạo kế hoạch'}
            </button>
          </>
        )}
      >
        <form id="ai-plan-create-form" className="ai-inline-form" onSubmit={handleCreate}>
          <Field label="Tên kế hoạch" required className="full">
            <input
              className="input"
              value={form.title}
              maxLength={255}
              onChange={handleChange('title')}
              required
            />
          </Field>
          <Field label="Mục tiêu" className="full">
            <textarea
              className="input"
              rows="2"
              maxLength={2000}
              value={form.goal}
              placeholder="Ví dụ: Ôn tập để đạt GPA học kỳ trên 8.0"
              onChange={handleChange('goal')}
            />
          </Field>
          <Field label="Ngày bắt đầu">
            <input className="input" type="date" value={form.startDate} onChange={handleChange('startDate')} />
          </Field>
          <Field label="Ngày kết thúc">
            <input className="input" type="date" value={form.endDate} onChange={handleChange('endDate')} />
          </Field>
          <Field label="Số giờ mỗi ngày">
            <input
              className="input"
              type="number"
              min="1"
              max="24"
              value={form.targetHoursPerDay}
              onChange={handleChange('targetHoursPerDay')}
            />
          </Field>
        </form>
      </Modal>

      <ConfirmDialog
        open={deletingId !== null}
        title="Xóa kế hoạch học tập?"
        message="Toàn bộ nhiệm vụ trong kế hoạch sẽ bị xóa và không thể khôi phục."
        confirmLabel="Xóa kế hoạch"
        loading={false}
        onConfirm={handleDelete}
        onCancel={() => setDeletingId(null)}
      />
    </>
  );
};

export default AIStudyPlanner;
