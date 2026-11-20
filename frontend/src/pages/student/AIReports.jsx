import React, { useEffect, useState } from 'react';
import { aiChatApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import {
  Alert, EmptyState, LoadingBlock, PageHeader, StatCard,
} from '../../components/UI';
import {
  AwardIcon, BookIcon, ClipboardIcon, FileTextIcon, RefreshIcon, TrendingIcon,
} from '../../components/Icons';
import {
  downloadBlob, formatDateTime, formatScore, getErrorMessage,
} from '../../utils/format';
import './ai-pages.css';

const unwrap = (res) => res?.data?.data ?? res?.data ?? null;

const AIReports = () => {
  const toast = useToast();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await aiChatApi.getReport();
      setReport(unwrap(res));
    } catch (err) {
      toast.error('Không tải được báo cáo học tập', getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleExport = async () => {
    setExporting(true);
    try {
      const res = await aiChatApi.downloadReportExcel();
      downloadBlob(res.data, 'AI_Academic_Report.xlsx');
      toast.success('Đã tải báo cáo Excel');
    } catch (err) {
      toast.error('Không thể xuất báo cáo', getErrorMessage(err));
    } finally {
      setExporting(false);
    }
  };

  if (loading) return <LoadingBlock text="Đang lập báo cáo học tập..." />;

  return (
    <>
      <PageHeader
        title="Báo cáo học tập AI"
        subtitle="Báo cáo được tổng hợp từ điểm đã công bố và dữ liệu đăng ký học phần trong hệ thống."
        actions={(
          <>
            <button type="button" className="btn btn-secondary" onClick={load}>
              <RefreshIcon /> Tải lại
            </button>
            <button type="button" className="btn btn-primary" onClick={handleExport} disabled={exporting || !report}>
              <FileTextIcon /> {exporting ? 'Đang xuất...' : 'Xuất Excel'}
            </button>
          </>
        )}
      />

      {!report ? (
        <EmptyState
          icon={<FileTextIcon />}
          title="Chưa có báo cáo"
          description="Nhấn «Tải lại» để hệ thống lập báo cáo từ dữ liệu hiện có."
        />
      ) : (
        <>
          <Alert tone="info">{report.dataAvailabilityNote || 'Báo cáo được tạo từ dữ liệu hệ thống.'}</Alert>

          <div className="stats-grid" style={{ marginTop: 16 }}>
            <StatCard label="GPA hệ 10" value={formatScore(report.gpa10)} icon={<TrendingIcon />} tone="blue" />
            <StatCard label="GPA hệ 4" value={formatScore(report.gpa4)} icon={<AwardIcon />} tone="green" />
            <StatCard label="Tín chỉ tích lũy" value={report.accumulatedCredits ?? '—'} icon={<BookIcon />} tone="amber" />
            <StatCard label="Tín chỉ đang học" value={report.activeCredits ?? '—'} icon={<ClipboardIcon />} tone="blue" />
          </div>

          <div className="ai-panel" style={{ marginTop: 16 }}>
            <div className="ai-panel-head">
              <div>
                <div className="ai-panel-title">Thông tin báo cáo</div>
                <div className="ai-panel-sub">
                  {report.studentCode || '—'} · {report.fullName || '—'} · {report.className || '—'}
                </div>
              </div>
              <span className="ai-pill">{formatDateTime(report.generatedAt)}</span>
            </div>
            <div className="ai-note">
              Nguồn dữ liệu: {(report.sources || []).join(', ') || 'Hệ thống SIS'}
            </div>
          </div>

          <div className="ai-page-grid ai-grid-2" style={{ marginTop: 16 }}>
            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Kết quả theo học kỳ</div>
                  <div className="ai-panel-sub">GPA và số tín chỉ đạt được từng học kỳ.</div>
                </div>
              </div>
              {(report.semesters || []).length === 0 ? (
                <div className="ai-empty-inline">Chưa có dữ liệu học kỳ.</div>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Học kỳ</th>
                        <th>GPA</th>
                        <th>Tín chỉ đạt</th>
                        <th>Đạt</th>
                        <th>Chưa đạt</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.semesters.map((item, index) => (
                        <tr key={`${item.semester}-${index}`}>
                          <td>{item.semester || '—'}</td>
                          <td><strong>{formatScore(item.gpa10)}</strong></td>
                          <td>{item.earnedCredits ?? '—'}</td>
                          <td>{item.passedCount ?? '—'}</td>
                          <td>{item.failedCount ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="ai-panel">
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Học phần điểm thấp</div>
                  <div className="ai-panel-sub">Những môn cần ưu tiên cải thiện trong học kỳ tới.</div>
                </div>
              </div>
              {(report.lowScoreCourses || []).length === 0 ? (
                <Alert tone="success">Không có học phần điểm thấp được ghi nhận.</Alert>
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
                      {report.lowScoreCourses.map((item, index) => (
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

          <div className="ai-panel" style={{ marginTop: 16 }}>
            <div className="ai-panel-head">
              <div>
                <div className="ai-panel-title">Mục tiêu cải thiện</div>
                <div className="ai-panel-sub">Đề xuất dựa trên các học phần chưa đạt.</div>
              </div>
            </div>
            {(report.improvementTargets || []).length === 0 ? (
              <div className="ai-empty-inline">Chưa có mục tiêu cải thiện.</div>
            ) : (
              <div className="ai-recommend-list">
                {report.improvementTargets.map((item, index) => (
                  <div className="ai-recommend-item" key={`${item.courseCode}-${index}`}>
                    <div className="ai-recommend-head">
                      <span className="ai-recommend-title">
                        {item.courseCode || '—'} · {item.courseName || '—'}
                      </span>
                      <span className="ai-pill HIGH">Ưu tiên cao</span>
                    </div>
                    <div className="ai-recommend-desc">
                     Điểm hiện tại {formatScore(item.finalScore)} · {item.classification || 'Chưa đạt'}.
                      {' '}Nên ôn tập lại trước khi đăng ký học phần tiếp theo.
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {(report.activeEnrollments || []).length > 0 && (
            <div className="ai-panel" style={{ marginTop: 16 }}>
              <div className="ai-panel-head">
                <div>
                  <div className="ai-panel-title">Học phần đang theo học</div>
                  <div className="ai-panel-sub">Thông tin đăng ký hiện tại của bạn.</div>
                </div>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Môn học</th>
                      <th>Mã lớp</th>
                      <th>Tín chỉ</th>
                      <th>Lịch học</th>
                      <th>Giảng viên</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.activeEnrollments.map((item, index) => (
                      <tr key={`${item.enrollmentId}-${index}`}>
                        <td>
                          <div className="cell-strong">{item.courseName || '—'}</div>
                          <div className="cell-muted">{item.courseCode}</div>
                        </td>
                        <td>{item.sectionCode || '—'}</td>
                        <td>{item.credits ?? '—'}</td>
                        <td className="cell-muted">{item.schedule || '—'}</td>
                        <td className="cell-muted">{item.lecturer || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </>
  );
};

export default AIReports;
