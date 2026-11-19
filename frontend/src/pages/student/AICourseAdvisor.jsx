import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { aiCourseAdvisorApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  Alert, EmptyState, Field, LoadingBlock, PageHeader, StatCard,
} from '../../components/UI';
import { AwardIcon, BookIcon, ClipboardIcon, RefreshIcon, SparklesIcon } from '../../components/Icons';
import { formatDateTime, getErrorMessage } from '../../utils/format';
import './ai-pages.css';

const unwrap = (res) => res?.data?.data ?? res?.data ?? null;

const STATUS_LABELS = {
  RECOMMENDED: 'Nên học',
  MUST_RETAKE: 'Cần học lại',
  SCHEDULE_CONFLICT: 'Trùng lịch',
  SECTIONS_FULL: 'Đã đầy chỗ',
  PREREQUISITE_NOT_MET: 'Chưa đủ điều kiện',
  ALREADY_COMPLETED: 'Đã hoàn thành',
  CURRENTLY_ENROLLED: 'Đang học',
};

const CourseCard = ({ item }) => (
  <div className="ai-course-card">
    <div className="ai-course-title">{item.courseName || '—'}</div>
    <div className="ai-course-meta">
      <span className="chip">{item.courseCode || '—'}</span>
      <span>{item.credits ?? '—'} tín chỉ</span>
      <span className={`ai-pill ${item.priority || 'LOW'}`}>{STATUS_LABELS[item.status] || item.status || 'Gợi ý'}</span>
    </div>
    <div className="ai-reason">{item.reason || 'Không có giải thích bổ sung.'}</div>
    {Array.isArray(item.missingPrerequisites) && item.missingPrerequisites.length > 0 && (
      <div className="ai-note" style={{ marginTop: 6 }}>
        Môn tiên quyết chưa đạt: {item.missingPrerequisites.join(', ')}
      </div>
    )}
    {Array.isArray(item.availableSections) && item.availableSections.length > 0 && (
      <div className="ai-section-list">
        {item.availableSections.slice(0, 4).map((section) => (
          <div className="ai-section-row" key={section.sectionId}>
            <span>
              <strong>{section.sectionCode || '—'}</strong>
              {' · '}{section.lecturerName || 'Chưa phân công'}
              {' · '}{section.schedule || 'Chưa có lịch'}
            </span>
            <span className="ai-pill">
              {section.remainingSeats ?? '—'} chỗ · {section.isOpen ? 'Đang mở' : 'Đã đóng'}
            </span>
          </div>
        ))}
      </div>
    )}
  </div>
);

const AICourseAdvisor = () => {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    careerOrientation: '',
    targetCredits: '',
    customNote: '',
  });

  const load = async () => {
    setLoading(true);
    try {
      const res = await aiCourseAdvisorApi.getRecommendations();
      setData(unwrap(res));
    } catch (err) {
      toast.error('Không tải được gợi ý học phần', getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleChange = (key) => (event) => {
    setForm((prev) => ({ ...prev, [key]: event.target.value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      const payload = {
        careerOrientation: form.careerOrientation || undefined,
        targetCredits: form.targetCredits === '' ? undefined : Number(form.targetCredits),
        customNote: form.customNote || undefined,
      };
      const res = await aiCourseAdvisorApi.recommend(payload);
      setData(unwrap(res));
      toast.success('Đã cập nhật gợi ý học phần');
    } catch (err) {
      toast.error('Không thể tư vấn học phần', getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <LoadingBlock text="Đang phân tích học phần phù hợp..." />;

  return (
    <>
      <PageHeader
        title="Tư vấn học phần AI"
        subtitle="Gợi ý môn học dựa trên điều kiện tiên quyết, tín chỉ đã tích lũy, lịch học và định hướng của bạn."
        actions={(
          <button type="button" className="btn btn-secondary" onClick={load}>
            <RefreshIcon /> Tải lại
          </button>
        )}
      />

      <div className="ai-panel">
        <div className="ai-panel-head">
          <div>
            <div className="ai-panel-title">Tuỳ chỉnh gợi ý</div>
            <div className="ai-panel-sub">Để trống nếu bạn muốn hệ thống tự phân tích.</div>
          </div>
        </div>
        <form className="ai-inline-form" onSubmit={handleSubmit}>
          <Field label="Định hướng nghề nghiệp">
            <input
              className="input"
              value={form.careerOrientation}
              maxLength={255}
              placeholder="Ví dụ: Phát triển phần mềm"
              onChange={handleChange('careerOrientation')}
            />
          </Field>
          <Field label="Số tín chỉ mong muốn">
            <input
              className="input"
              type="number"
              min="1"
              max="30"
              value={form.targetCredits}
              placeholder="9 - 18"
              onChange={handleChange('targetCredits')}
            />
          </Field>
          <Field label="Ghi chú thêm" className="full">
            <textarea
              className="input"
              rows="2"
              maxLength={2000}
              value={form.customNote}
              placeholder="Môn bạn muốn ưu tiên, thời gian rảnh..."
              onChange={handleChange('customNote')}
            />
          </Field>
          <div className="full" style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              <SparklesIcon /> {submitting ? 'Đang phân tích...' : 'Gợi ý cho tôi'}
            </button>
          </div>
        </form>
      </div>

      {!data ? (
        <div style={{ marginTop: 16 }}>
          <EmptyState
            title="Chưa có gợi ý"
            description="Nhấn «Gợi ý cho tôi» để hệ thống phân tích học phần phù hợp."
          />
        </div>
      ) : (
        <>
          <div className="stats-grid" style={{ marginTop: 16 }}>
            <StatCard label="Tín chỉ tích lũy" value={data.accumulatedCredits ?? '—'} icon={<AwardIcon />} tone="green" />
            <StatCard label="Tín chỉ đang học" value={data.activeCredits ?? '—'} icon={<BookIcon />} tone="blue" />
            <StatCard label="Môn nên cân nhắc" value={data.recommendedCourses?.length ?? 0} icon={<ClipboardIcon />} tone="amber" />
            <StatCard label="Môn cần lưu ý" value={data.notRecommendedCourses?.length ?? 0} icon={<ClipboardIcon />} tone="red" />
          </div>

          <div className="ai-panel" style={{ marginTop: 16 }}>
            <div className="ai-panel-head">
              <div>
                <div className="ai-panel-title">Nhận xét từ cố vấn AI</div>
                <div className="ai-panel-sub">Cập nhật lúc {formatDateTime(data.generatedAt)}.</div>
              </div>
            </div>
            <div className="ai-insight-summary">{data.advisorSummary || 'Chưa có nhận xét.'}</div>
            <Alert tone="info" style={{ marginTop: 12 }}>{data.disclaimer || 'Gợi ý chỉ mang tính tham khảo.'}</Alert>
          </div>

          <div className="ai-panel" style={{ marginTop: 16 }}>
            <div className="ai-panel-head">
              <div>
                <div className="ai-panel-title">Học phần nên đăng ký</div>
                <div className="ai-panel-sub">Đã xét điều kiện tiên quyết và tình trạng lớp học phần.</div>
              </div>
              <Link to="/student/courses" className="btn btn-secondary btn-sm">Xem danh mục môn</Link>
            </div>
            {(data.recommendedCourses || []).length === 0 ? (
              <div className="ai-empty-inline">Chưa có học phần nào được đề xuất.</div>
            ) : (
              <div className="ai-course-grid">
                {data.recommendedCourses.map((item, index) => (
                  <CourseCard item={item} key={`${item.courseCode}-${index}`} />
                ))}
              </div>
            )}
          </div>

          {(data.notRecommendedCourses || []).length > 0 && (
            <div className="ai-panel" style={{ marginTop: 16 }}>
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Học phần chưa phù hợp</div>
                  <div className="ai-panel-sub">Bạn nên xử lý các điều kiện bên dưới trước khi đăng ký.</div>
                </div>
              </div>
              <div className="ai-course-grid">
                {data.notRecommendedCourses.map((item, index) => (
                  <CourseCard item={item} key={`${item.courseCode}-${index}`} />
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </>
  );
};

export default AICourseAdvisor;
