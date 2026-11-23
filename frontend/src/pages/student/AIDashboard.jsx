import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  aiChatApi, aiInsightsApi, aiStudyPlanApi,
} from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  AwardIcon, BookIcon, CalendarIcon, ClipboardIcon,
  MessageSquareIcon, SparklesIcon, TrendingIcon,
} from '../../components/Icons';
import { Alert, EmptyState, LoadingBlock, PageHeader, StatCard } from '../../components/UI';
import { formatDateTime, formatScore, getErrorMessage } from '../../utils/format';
import {
  CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import './ai-pages.css';

const unwrap = (res) => res?.data?.data ?? res?.data ?? null;

const AIDashboard = () => {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [overview, setOverview] = useState(null);
  const [insights, setInsights] = useState(null);
  const [plans, setPlans] = useState([]);
  const [conversations, setConversations] = useState([]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const [overviewRes, insightsRes, plansRes, conversationsRes] = await Promise.allSettled([
        aiChatApi.getOverview(),
        aiInsightsApi.getInsights(),
        aiStudyPlanApi.getAll(),
        aiChatApi.getConversations(),
      ]);
      if (!mounted) return;
      if (overviewRes.status === 'fulfilled') setOverview(unwrap(overviewRes.value));
      else toast.error('Không tải được tổng quan AI', getErrorMessage(overviewRes.reason));
      if (insightsRes.status === 'fulfilled') setInsights(unwrap(insightsRes.value));
      if (plansRes.status === 'fulfilled') setPlans(unwrap(plansRes.value) || []);
      if (conversationsRes.status === 'fulfilled') setConversations(unwrap(conversationsRes.value) || []);
      setLoading(false);
    })();
    return () => { mounted = false; };
  }, []);

  const chartData = useMemo(() => (insights?.semesterInsights || []).map((item) => ({
    name: String(item.semesterName || 'HK').replace('Học kỳ ', 'HK'),
    gpa: Number(item.gpa10 || 0),
    credits: Number(item.creditsEarned || 0),
  })), [insights]);

  const activePlan = useMemo(() => (
    (plans || []).find((plan) => plan.status === 'ACTIVE') || (plans || [])[0] || null
  ), [plans]);

  const openChat = () => window.dispatchEvent(new CustomEvent('sis-ai:open-chat'));

  if (loading) return <LoadingBlock text="Đang tải trang tổng quan AI..." />;

  return (
    <>
      <PageHeader
        title="Tổng quan AI"
        subtitle="Thông tin học tập thực tế, gợi ý cải thiện và các công cụ AI dành riêng cho sinh viên."
        actions={(
          <button type="button" className="btn btn-primary" onClick={openChat}>
            <SparklesIcon /> Mở trợ lý AI
          </button>
        )}
      />

      <div className="stats-grid">
        <StatCard label="GPA hệ 10" value={formatScore(overview?.gpa10)} icon={<TrendingIcon />} tone="blue" />
        <StatCard label="GPA hệ 4" value={formatScore(overview?.gpa4)} icon={<TrendingIcon />} tone="indigo" />
        <StatCard label="Tín chỉ tích lũy" value={overview?.accumulatedCredits ?? '—'} icon={<AwardIcon />} tone="green" />
        <StatCard label="Tín chỉ đang học" value={overview?.activeCredits ?? '—'} icon={<BookIcon />} tone="amber" />
        <StatCard label="Học phần chưa đạt" value={overview?.failedCount ?? '—'} icon={<ClipboardIcon />} tone="red" />
      </div>

      <div className="ai-page-grid ai-grid-2" style={{ marginTop: 16 }}>
        <div className="ai-panel">
          <div className="ai-panel-head">
            <div>
              <div className="ai-panel-title">Xu hướng học tập</div>
              <div className="ai-panel-sub">GPA theo học kỳ từ dữ liệu điểm đã công bố.</div>
            </div>
            <Link to="/student/ai-insights" className="btn btn-secondary btn-sm">Chi tiết</Link>
          </div>
          {chartData.length === 0 ? (
            <div className="ai-empty-inline">Chưa có đủ dữ liệu học tập để hiển thị biểu đồ.</div>
          ) : (
            <div className="ai-chart-wrap">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="name" stroke="var(--muted)" fontSize={11} />
                  <YAxis domain={[0, 10]} stroke="var(--muted)" fontSize={11} />
                  <Tooltip />
                  <Line type="monotone" dataKey="gpa" name="GPA 10" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>

        <div className="ai-panel">
          <div className="ai-panel-head">
            <div>
              <div className="ai-panel-title">Nhận xét nhanh từ AI</div>
              <div className="ai-panel-sub">Tóm tắt dựa trên kết quả học tập hiện có.</div>
            </div>
            <Link to="/student/ai-insights" className="btn btn-secondary btn-sm">Xem tất cả</Link>
          </div>
          {insights?.aiSummary ? (
            <div className="ai-insight-summary">{insights.aiSummary}</div>
          ) : (
            <div className="ai-empty-inline">Chưa có nhận xét AI. Hãy làm mới dữ liệu phân tích.</div>
          )}
          <div className="ai-recommend-list" style={{ marginTop: 12 }}>
            {(insights?.recommendations || []).slice(0, 3).map((item, index) => (
              <div className="ai-recommend-item" key={`${item.title}-${index}`}>
                <div className="ai-recommend-head">
                  <span className="ai-recommend-title">{item.title || 'Gợi ý'}</span>
                  <span className={`ai-pill ${item.priority || 'MEDIUM'}`}>{item.type || 'Gợi ý'}</span>
                </div>
                <div className="ai-recommend-desc">{item.description || '—'}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="ai-page-grid ai-grid-2" style={{ marginTop: 16 }}>
        <div className="ai-panel">
          <div className="ai-panel-head">
            <div>
              <div className="ai-panel-title">Kế hoạch học tập gần đây</div>
              <div className="ai-panel-sub">Tiến độ thực hiện các nhiệm vụ đã lập.</div>
            </div>
            <Link to="/student/ai-study-planner" className="btn btn-secondary btn-sm">Quản lý</Link>
          </div>
          {activePlan ? (
            <>
              <div className="ai-plan-title">{activePlan.title || 'Kế hoạch học tập'}</div>
              <div className="ai-plan-meta">
                {activePlan.startDate || '—'} → {activePlan.endDate || '—'}
                {' · '}{activePlan.completedTasks || 0}/{activePlan.totalTasks || 0} nhiệm vụ
              </div>
              <div className="ai-progress-track">
                <div className="ai-progress-fill" style={{ width: `${Math.min(Number(activePlan.completionRate || 0), 100)}%` }} />
              </div>
              <div className="ai-note" style={{ marginTop: 6 }}>
                Hoàn thành {Math.round(Number(activePlan.completionRate || 0))}% · {activePlan.totalStudyMinutes || 0} phút học tập
              </div>
            </>
          ) : (
            <EmptyState
              icon={<CalendarIcon />}
              title="Chưa có kế hoạch học tập"
              description="Tạo kế hoạch đầu tiên để AI gợi ý lịch ôn tập phù hợp."
              action={<Link to="/student/ai-study-planner" className="btn btn-primary btn-sm">Tạo kế hoạch</Link>}
            />
          )}
        </div>

        <div className="ai-panel">
          <div className="ai-panel-head">
            <div>
              <div className="ai-panel-title">Hoạt động AI gần đây</div>
              <div className="ai-panel-sub">Các cuộc trò chuyện bạn đã thực hiện.</div>
            </div>
            <button type="button" className="btn btn-secondary btn-sm" onClick={openChat}>
              <MessageSquareIcon /> Trò chuyện ngay
            </button>
          </div>
          {conversations.length === 0 ? (
            <div className="ai-empty-inline">Bạn chưa có cuộc trò chuyện nào.</div>
          ) : (
            <div className="ai-recommend-list">
              {conversations.slice(0, 4).map((item) => (
                <div className="ai-recommend-item" key={item.id}>
                  <div className="ai-recommend-head">
                    <span className="ai-recommend-title">{item.title || 'Hội thoại học tập'}</span>
                    <span className="ai-pill">{formatDateTime(item.updatedAt || item.createdAt)}</span>
                  </div>
                  <div className="ai-recommend-desc">{item.lastMessage || 'Chưa có tin nhắn gần nhất.'}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="ai-panel" style={{ marginTop: 16 }}>
        <div className="ai-panel-head">
          <div>
            <div className="ai-panel-title">Truy cập nhanh</div>
            <div className="ai-panel-sub">Tất cả công cụ AI được gom tại đây.</div>
          </div>
        </div>
        <div className="ai-quick-actions">
          <button type="button" className="ai-quick-action" onClick={openChat}>Trợ lý AI Sinh viên</button>
          <Link to="/student/ai-insights" className="ai-quick-action">Nhận xét học tập</Link>
          <Link to="/student/ai-course-advisor" className="ai-quick-action">Tư vấn học phần</Link>
          <Link to="/student/ai-study-planner" className="ai-quick-action">Kế hoạch học tập</Link>
          <Link to="/student/ai-reports" className="ai-quick-action">Báo cáo học tập</Link>
          <Link to="/student/grades" className="ai-quick-action">Kết quả đã công bố</Link>
        </div>
        {!overview?.gpa10 && (
          <Alert tone="info" style={{ marginTop: 12 }}>
            Một số chỉ số có thể bằng 0 do hệ thống chưa có điểm đã công bố.
          </Alert>
        )}
      </div>
    </>
  );
};

export default AIDashboard;
