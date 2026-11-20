import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { aiCourseAdvisorApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  Alert, EmptyState, Field, LoadingBlock, PageHeader, Spinner, StatCard,
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

const MATCH_LABELS = {
  HIGH: 'Rất phù hợp',
  MEDIUM: 'Khá phù hợp',
  LOW: 'Ít phù hợp',
  NOT_SPECIFIED: 'Chưa xác định',
};

const InfoRow = ({ label, children }) => (
  <div className="ai-kv">
    <span>{label}</span>
    <strong>{children ?? 'Chưa có dữ liệu'}</strong>
  </div>
);

const CourseCard = ({ item, variant = 'current' }) => (
  <div className="ai-course-card">
    <div className="ai-course-title">{item.courseName || '—'}</div>
    <div className="ai-course-meta">
      <span className="chip">{item.courseCode || '—'}</span>
      <span>{item.credits ?? '—'} tín chỉ</span>
      <span className={`ai-pill ${item.priority || 'LOW'}`}>{STATUS_LABELS[item.status] || item.status || 'Gợi ý'}</span>
      {variant !== 'blocked' && item.orientationMatch && item.orientationMatch !== 'NOT_SPECIFIED' && (
        <span className="ai-pill">{MATCH_LABELS[item.orientationMatch] || item.orientationMatch}</span>
      )}
      {variant === 'deferred' && <span className="ai-pill">Có thể học kỳ sau</span>}
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
              {section.weeklyHours ? ` · ${section.weeklyHours} giờ/tuần` : ''}
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

const Panel = ({ title, subtitle, action, children, style }) => (
  <div className="ai-panel" style={{ marginTop: 16, ...style }}>
    <div className="ai-panel-head">
      <div>
        <div className="ai-panel-title">{title}</div>
        {subtitle && <div className="ai-panel-sub">{subtitle}</div>}
      </div>
      {action}
    </div>
    {children}
  </div>
);

const AICourseAdvisor = () => {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState({
    careerOrientation: '',
    targetCredits: '',
    customNote: '',
  });
  const requestSeq = useRef(0);

  const applyPayload = (payload) => {
    if (!payload || !payload.requestAnalysis || !payload.creditSummary) {
      setError('Backend chưa trả đủ dữ liệu phân tích yêu cầu. Vui lòng kiểm tra phiên bản backend.');
      return false;
    }
    setData(payload);
    return true;
  };

  const load = async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setError('');
    setData(null);
    try {
      const res = await aiCourseAdvisorApi.getRecommendations();
      if (seq !== requestSeq.current) return;
      if (!applyPayload(unwrap(res))) return;
    } catch (err) {
      if (seq !== requestSeq.current) return;
      const message = getErrorMessage(err);
      setError(message);
      toast.error('Không tải được gợi ý học phần', message);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleChange = (key) => (event) => {
    setForm((prev) => ({ ...prev, [key]: event.target.value }));
  };

  const submitRecommendation = async () => {
    setError('');
    const rawCredits = String(form.targetCredits || '').trim();
    const normalized = rawCredits
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase()
      .replace(/\s+/g, ' ');

    if (rawCredits && !/^(?:tu\s+)?\d{1,2}\s*(?:[-–—]\s*\d{1,2}|(?:den|to|till)\s*\d{1,2})?$/.test(normalized)) {
      setError('Số tín chỉ mong muốn phải là một số hoặc khoảng hợp lệ, ví dụ: 12 hoặc 12-15.');
      return;
    }
    const numbers = normalized.match(/\d+/g)?.map(Number) || [];
    if (numbers.some((value) => value < 1 || value > 30)
      || (numbers.length === 2 && numbers[0] > numbers[1])) {
      setError('Số tín chỉ mong muốn phải nằm trong khoảng 1-30 và khoảng phải tăng dần.');
      return;
    }

    const payload = {
      careerOrientation: form.careerOrientation.trim() || undefined,
      targetCredits: /^\d+$/.test(normalized) ? Number(normalized) : undefined,
      targetCreditsRange: rawCredits || undefined,
      customNote: form.customNote.trim() || undefined,
    };
    const seq = ++requestSeq.current;
    setSubmitting(true);
    setData(null);
    try {
      const res = await aiCourseAdvisorApi.recommend(payload);
      if (seq !== requestSeq.current) return;
      if (!applyPayload(unwrap(res))) return;
      toast.success('Đã cập nhật gợi ý học phần');
    } catch (err) {
      if (seq !== requestSeq.current) return;
      const message = getErrorMessage(err);
      setError(message);
      toast.error('Không thể tư vấn học phần', message);
    } finally {
      if (seq === requestSeq.current) setSubmitting(false);
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    submitRecommendation();
  };

  if (loading) return <LoadingBlock text="Đang phân tích học phần phù hợp..." />;

  const analysis = data?.requestAnalysis;
  const credit = data?.creditSummary;
  const suitability = data?.suitabilityAnalysis;
  const alternative = data?.alternativePlan;
  const recommended = data?.recommendedCourses || [];
  const deferred = data?.deferredCourses || [];
  const blocked = data?.notRecommendedCourses || [];

  return (
    <>
      <PageHeader
        title="Tư vấn học phần AI"
        subtitle="Gợi ý môn học dựa trên điều kiện tiên quyết, tín chỉ đã tích lũy, lịch học và yêu cầu cá nhân của bạn."
        actions={(
          <>
            <button type="button" className="btn btn-secondary" onClick={load} disabled={submitting}>
              <RefreshIcon /> Tải lại
            </button>
            <button type="button" className="btn btn-primary" onClick={submitRecommendation} disabled={submitting}>
              <SparklesIcon /> Tư vấn lại
            </button>
          </>
        )}
      />

      {error && <Alert tone="error" style={{ marginBottom: 12 }}>{error}</Alert>}

      <div className="ai-panel">
        <div className="ai-panel-head">
          <div>
            <div className="ai-panel-title">Tuỳ chỉnh gợi ý</div>
            <div className="ai-panel-sub">Thông tin bạn nhập sẽ được gửi tới Backend và kết hợp với dữ liệu học tập thực tế.</div>
          </div>
        </div>
        <form className="ai-inline-form" onSubmit={handleSubmit}>
          <Field label="Định hướng nghề nghiệp" hint="Ví dụ: Backend Developer, Data Engineer, AI Engineer">
            <input
              className="input"
              value={form.careerOrientation}
              maxLength={255}
              placeholder="Ví dụ: Backend Developer"
              onChange={handleChange('careerOrientation')}
              disabled={submitting}
            />
          </Field>
          <Field label="Số tín chỉ mong muốn" hint="Nhập một số hoặc khoảng, ví dụ: 12 hoặc 12-15">
            <input
              className="input"
              type="text"
              inputMode="numeric"
              maxLength={100}
              value={form.targetCredits}
              placeholder="Ví dụ: 12-15"
              onChange={handleChange('targetCredits')}
              disabled={submitting}
            />
          </Field>
          <Field label="Ghi chú thêm" className="full" hint="Ví dụ: ưu tiên Java, Spring Boot, cơ sở dữ liệu; muốn học kỳ nhẹ; tránh lịch dày">
            <textarea
              className="input"
              rows="2"
              maxLength={2000}
              value={form.customNote}
              placeholder="Ưu tiên Java, Spring Boot, cơ sở dữ liệu và các môn lập trình thực hành..."
              onChange={handleChange('customNote')}
              disabled={submitting}
            />
          </Field>
          <div className="full ai-form-actions">
            {submitting && (
              <span className="ai-inline-status"><Spinner /> Đang phân tích yêu cầu và dữ liệu học tập...</span>
            )}
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
          <Panel title="1. Phân tích yêu cầu" subtitle="Yêu cầu cá nhân được kết hợp cùng hồ sơ học tập thực tế.">
            <div className="ai-kv-grid">
              <InfoRow label="Định hướng nghề nghiệp">{analysis?.careerOrientation || 'Không nhập'}</InfoRow>
              <InfoRow label="Số tín chỉ mong muốn">
                {analysis?.targetCreditsMin != null
                  ? `${analysis.targetCreditsMin} - ${analysis.targetCreditsMax} tín chỉ`
                  : 'Không yêu cầu cụ thể'}
              </InfoRow>
              <InfoRow label="Giới hạn hệ thống">{analysis?.effectiveCreditsMax ?? data.maxCreditsPerSemester} tín chỉ / kỳ</InfoRow>
              <InfoRow label="Ghi chú thêm">{analysis?.customNote || 'Để trống'}</InfoRow>
              <InfoRow label="Yêu cầu nhận diện">
                {analysis?.detectedPreferences?.length ? analysis.detectedPreferences.join(', ') : 'Không có'}
              </InfoRow>
              <InfoRow label="Tình trạng học tập">{analysis?.academicSummary || 'Chưa có dữ liệu'}</InfoRow>
            </div>
          </Panel>

          <div className="stats-grid" style={{ marginTop: 16 }}>
            <StatCard label="Tín chỉ tích lũy" value={data.accumulatedCredits ?? '—'} icon={<AwardIcon />} tone="green" />
            <StatCard label="Tín chỉ đang học" value={data.activeCredits ?? '—'} icon={<BookIcon />} tone="blue" />
            <StatCard label="Môn nên cân nhắc" value={recommended.length} icon={<ClipboardIcon />} tone="amber" />
            <StatCard label="Môn cần lưu ý" value={blocked.length} icon={<ClipboardIcon />} tone="red" />
          </div>

          <Panel title="Nhận xét từ cố vấn AI" subtitle={`Cập nhật lúc ${formatDateTime(data.generatedAt)}.`}>
            <div className="ai-insight-summary">{data.advisorSummary || 'Chưa có nhận xét.'}</div>
            <Alert tone="info" style={{ marginTop: 12 }}>{data.disclaimer || 'Gợi ý chỉ mang tính tham khảo.'}</Alert>
          </Panel>

          <Panel
            title="2. Học phần đề xuất cho kỳ này"
            subtitle="Đã xác thực điều kiện tiên quyết, chỗ trống, tín chỉ và lịch học."
            action={<Link to="/student/courses" className="btn btn-secondary btn-sm">Xem danh mục môn</Link>}
          >
            {recommended.length === 0 ? (
              <div className="ai-empty-inline">Chưa có học phần nào được đề xuất.</div>
            ) : (
              <div className="ai-course-grid">
                {recommended.map((item, index) => (
                  <CourseCard item={item} key={`${item.courseCode}-${index}`} />
                ))}
              </div>
            )}
          </Panel>

          <Panel title="3. Tổng kết tín chỉ">
            <div className="ai-kv-grid">
              <InfoRow label="Số học phần đề xuất">{credit?.recommendedCourseCount ?? recommended.length}</InfoRow>
              <InfoRow label="Tổng tín chỉ đề xuất">{credit?.totalRecommendedCredits ?? '—'}</InfoRow>
              <InfoRow label="Mong muốn">
                {credit?.targetCreditsMin != null
                  ? `${credit.targetCreditsMin} - ${credit.targetCreditsMax} tín chỉ`
                  : 'Không yêu cầu cụ thể'}
              </InfoRow>
              <InfoRow label="Chênh lệch">{credit?.creditDifference ?? '—'} tín chỉ</InfoRow>
              <InfoRow label="Đáp ứng mong muốn">{credit?.withinTargetRange ? 'Có' : 'Không'}</InfoRow>
              <InfoRow label="Giới hạn hệ thống">{credit?.maxCreditsPerSemester ?? '—'} tín chỉ / kỳ</InfoRow>
            </div>
            {credit?.explanation && <div className="ai-note" style={{ marginTop: 10 }}>{credit.explanation}</div>}
          </Panel>

          <Panel title="4. Phân tích mức độ phù hợp">
            <div className="ai-insight-summary">{suitability?.explanation || 'Chưa có phân tích bổ sung.'}</div>
            {Array.isArray(suitability?.requiredConditions) && suitability.requiredConditions.length > 0 && (
              <ul className="ai-condition-list">
                {suitability.requiredConditions.map((condition) => <li key={condition}>{condition}</li>)}
              </ul>
            )}
          </Panel>

          {alternative && (
            <Panel title="5. Phương án thay thế" subtitle={alternative.title}>
              <div className="ai-insight-summary">{alternative.reason}</div>
              <div className="ai-note" style={{ marginTop: 8 }}>
                Tổng {alternative.totalCredits ?? '—'} tín chỉ · {alternative.courses?.length ?? 0} học phần
              </div>
              {Array.isArray(alternative.courses) && alternative.courses.length > 0 && (
                <div className="ai-course-grid" style={{ marginTop: 10 }}>
                  {alternative.courses.map((item, index) => (
                    <CourseCard item={item} variant="deferred" key={`${item.courseCode}-alt-${index}`} />
                  ))}
                </div>
              )}
            </Panel>
          )}

          {deferred.length > 0 && (
            <Panel title="Có thể học ở kỳ sau" subtitle="Học phần vẫn đủ điều kiện nhưng nằm ngoài phương án kỳ này.">
              <div className="ai-course-grid">
                {deferred.map((item, index) => (
                  <CourseCard item={item} variant="deferred" key={`${item.courseCode}-${index}`} />
                ))}
              </div>
            </Panel>
          )}

          {blocked.length > 0 && (
            <Panel title="Học phần chưa phù hợp" subtitle="Bạn nên xử lý các điều kiện bên dưới trước khi đăng ký.">
              <div className="ai-course-grid">
                {blocked.map((item, index) => (
                  <CourseCard item={item} variant="blocked" key={`${item.courseCode}-${index}`} />
                ))}
              </div>
            </Panel>
          )}
        </>
      )}
    </>
  );
};

export default AICourseAdvisor;
