import React, { useEffect, useMemo, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { aiInsightsApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { AwardIcon, RefreshIcon, TrendingIcon, BookIcon } from '../../components/Icons';
import {
  Alert, EmptyState, LoadingBlock, PageHeader, StatCard,
} from '../../components/UI';
import { formatDateTime, formatScore, getErrorMessage } from '../../utils/format';
import './ai-pages.css';

const unwrap = (res) => res?.data?.data ?? res?.data ?? null;

const AIInsights = () => {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      const res = await aiInsightsApi.getInsights();
      setData(unwrap(res));
    } catch (err) {
      toast.error('Không tải được dữ liệu phân tích', getErrorMessage(err));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const res = await aiInsightsApi.refreshInsights();
      setData(unwrap(res));
      toast.success('Đã làm mới dữ liệu phân tích');
    } catch (err) {
      toast.error('Không thể làm mới dữ liệu', getErrorMessage(err));
      setRefreshing(false);
    }
  };

  const chartData = useMemo(() => (data?.semesterInsights || []).map((item) => ({
    name: String(item.semesterName || 'HK').replace('Học kỳ ', 'HK'),
    gpa: Number(item.gpa10 || 0),
    credits: Number(item.creditsEarned || 0),
  })), [data]);

  if (loading) return <LoadingBlock text="Đang phân tích dữ liệu học tập..." />;

  return (
    <>
      <PageHeader
        title="Nhận xét học tập AI"
        subtitle="GPA, tín chỉ, xu hướng theo học kỳ và khuyến nghị cải thiện dựa trên dữ liệu thực tế."
        actions={(
          <button type="button" className="btn btn-primary" onClick={handleRefresh} disabled={refreshing}>
            <RefreshIcon /> {refreshing ? 'Đang làm mới...' : 'Làm mới phân tích'}
          </button>
        )}
      />

      {!data ? (
        <EmptyState
          title="Chưa có dữ liệu phân tích"
          description="Chạy làm mới để hệ thống tổng hợp nhận xét từ kết quả học tập của bạn."
          action={<button type="button" className="btn btn-primary" onClick={handleRefresh}>Làm mới ngay</button>}
        />
      ) : (
        <>
          <div className="stats-grid">
            <StatCard label="GPA hệ 10" value={formatScore(data.gpa10)} icon={<TrendingIcon />} tone="blue" />
            <StatCard label="GPA hệ 4" value={formatScore(data.gpa4)} icon={<AwardIcon />} tone="green" />
            <StatCard label="Tín chỉ hoàn thành" value={data.completedCredits ?? '—'} icon={<BookIcon />} tone="amber" />
            <StatCard label="Học phần chưa đạt" value={data.totalFailedCourses ?? '—'} icon={<AwardIcon />} tone="red" />
          </div>

          <div className="ai-page-grid ai-grid-2" style={{ marginTop: 16 }}>
            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Xu hướng GPA theo học kỳ</div>
                  <div className="ai-panel-sub">So sánh điểm trung bình các học kỳ đã có điểm công bố.</div>
                </div>
              </div>
              {chartData.length === 0 ? (
                <div className="ai-empty-inline">Chưa có dữ liệu để vẽ biểu đồ.</div>
              ) : (
                <div className="ai-chart-wrap">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="name" stroke="var(--muted)" fontSize={11} />
                      <YAxis domain={[0, 10]} stroke="var(--muted)" fontSize={11} />
                      <Tooltip />
                      <Legend />
                      <Line type="monotone" dataKey="gpa" name="GPA 10" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>

            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Tín chỉ tích lũy theo học kỳ</div>
                  <div className="ai-panel-sub">Số tín chỉ đã đạt được sau mỗi học kỳ.</div>
                </div>
              </div>
              {chartData.length === 0 ? (
                <div className="ai-empty-inline">Chưa có dữ liệu để vẽ biểu đồ.</div>
              ) : (
                <div className="ai-chart-wrap">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="name" stroke="var(--muted)" fontSize={11} />
                      <YAxis stroke="var(--muted)" fontSize={11} />
                      <Tooltip />
                      <Bar dataKey="credits" name="Tín chỉ đạt" fill="#22c55e" radius={[5, 5, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          </div>

          <div className="ai-page-grid ai-grid-2" style={{ marginTop: 16 }}>
            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Nhận xét từ AI</div>
                  <div className="ai-panel-sub">Cập nhật lúc {formatDateTime(data.generatedAt)}.</div>
                </div>
              </div>
              {data.aiSummary ? (
                <div className="ai-insight-summary">{data.aiSummary}</div>
              ) : (
                <div className="ai-empty-inline">Chưa có nhận xét tự động.</div>
              )}

              <div style={{ marginTop: 14 }}>
                <div className="ai-panel-title" style={{ fontSize: 13, marginBottom: 8 }}>Khuyến nghị</div>
                {(data.recommendations || []).length === 0 ? (
                  <div className="ai-empty-inline">Chưa có khuyến nghị nào.</div>
                ) : (
                  <div className="ai-recommend-list">
                    {data.recommendations.map((item, index) => (
                      <div className="ai-recommend-item" key={`${item.title}-${index}`}>
                        <div className="ai-recommend-head">
                          <span className="ai-recommend-title">{item.title || 'Khuyến nghị'}</span>
                          <span className={`ai-pill ${item.priority || 'MEDIUM'}`}>{item.type || 'Gợi ý'}</span>
                        </div>
                        <div className="ai-recommend-desc">{item.description || '—'}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Học phần điểm thấp cần cải thiện</div>
                  <div className="ai-panel-sub">Dựa trên điểm đã công bố trong hệ thống.</div>
                </div>
              </div>
              {(data.lowScoreCourses || []).length === 0 ? (
                <Alert tone="success">Hiện chưa ghi nhận học phần nào có điểm thấp.</Alert>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Môn học</th>
                        <th>Học kỳ</th>
                        <th>Điểm</th>
                        <th>Xếp loại</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.lowScoreCourses.map((item, index) => (
                        <tr key={`${item.courseCode}-${index}`}>
                          <td>
                            <div className="cell-strong">{item.courseName || '—'}</div>
                            <div className="cell-muted">{item.courseCode}</div>
                          </td>
                          <td className="cell-muted">{item.semester || '—'}</td>
                          <td><strong>{formatScore(item.finalScore)}</strong></td>
                          <td>{item.classification || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </>
  );
};

export default AIInsights;
