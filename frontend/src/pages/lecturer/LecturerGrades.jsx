import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { courseSectionApi, gradeApi, gradebookApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { AwardIcon, BookIcon, ClipboardIcon, LockIcon, SaveIcon, UsersIcon } from '../../components/Icons';
import {
  Alert, EmptyState, Field, LoadingBlock, PageHeader, Pagination, StatusBadge,
} from '../../components/UI';
import { formatScore, getErrorMessage, gradeLabel } from '../../utils/format';

const PAGE_SIZE = 10;

const buildDraft = (detail) => {
  const next = {};
  (detail?.enrollments || []).forEach((row) => {
    if (detail.components?.length) {
      const scores = {};
      detail.components.forEach((component) => {
        const value = row.componentScores?.[component.id];
        scores[component.id] = value === null || value === undefined ? '' : String(value);
      });
      next[row.enrollmentId] = scores;
    } else {
      next[row.enrollmentId] = row.finalScore === null || row.finalScore === undefined ? '' : String(row.finalScore);
    }
  });
  return next;
};

const previewFinal = (detail, draftRow) => {
  if (!detail?.components?.length || !draftRow) return null;
  let sum = 0;
  for (const component of detail.components) {
    const required = component.required !== false;
    const raw = draftRow[component.id];
    if (raw === '' || raw === null || raw === undefined) {
      if (required) return null;
      continue;
    }
    const score = Number(raw);
    const max = Number(component.maxScore) || 10;
    if (Number.isNaN(score)) return null;
    sum += (score / max) * Number(component.weight);
  }
  return sum;
};

const LecturerGrades = () => {
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [sections, setSections] = useState([]);
  const [selected, setSelected] = useState(searchParams.get('section') || '');
  const [detail, setDetail] = useState(null);
  const [draft, setDraft] = useState({});
  const [loadingSections, setLoadingSections] = useState(true);
  const [loadingList, setLoadingList] = useState(false);
  const [savingId, setSavingId] = useState(null);
  const [page, setPage] = useState(1);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        let res;
        try { res = await courseSectionApi.getMine(); } catch { res = await courseSectionApi.getAll(); }
        if (!mounted) return;
        setSections(res.data || []);
      } catch {
        if (mounted) toast.error('Không tải được danh sách lớp');
      } finally {
        if (mounted) setLoadingSections(false);
      }
    };
    load();
    return () => { mounted = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selected) {
      setDetail(null);
      setDraft({});
      return undefined;
    }
    let mounted = true;
    setLoadingList(true);
    gradebookApi.getSectionDetail(selected)
      .then((res) => {
        if (!mounted) return;
        setDetail(res.data);
        setDraft(buildDraft(res.data));
        setPage(1);
      })
      .catch((err) => {
        if (!mounted) return;
        setDetail(null);
        setDraft({});
        toast.error('Không tải được bảng điểm', getErrorMessage(err));
      })
      .finally(() => mounted && setLoadingList(false));
    return () => { mounted = false; };
  }, [selected]); // eslint-disable-line react-hooks/exhaustive-deps

  const currentSection = sections.find((s) => String(s.id) === String(selected));
  const components = detail?.components || [];
  const componentMode = components.length > 0;
  const editable = detail?.status === 'OPEN';
  const enrollments = detail?.enrollments || [];
  const pageItems = enrollments.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const totalWeight = components.reduce((sum, c) => sum + Number(c.weight || 0), 0);

  const handleChange = (enrollmentId, field, value) => {
    setDraft((prev) => {
      const current = prev[enrollmentId];
      if (componentMode) {
        return { ...prev, [enrollmentId]: { ...(current || {}), [field]: value } };
      }
      return { ...prev, [enrollmentId]: value };
    });
  };

  const validateRow = (row) => {
    if (!componentMode) {
      const value = draft[row.enrollmentId];
      if (value === '' || value === null || value === undefined) return 'Vui lòng nhập điểm trước khi lưu.';
      const num = Number(value);
      if (Number.isNaN(num) || num < 0 || num > 10) return 'Điểm phải nằm trong khoảng từ 0 đến 10.';
      return '';
    }
    const values = draft[row.enrollmentId] || {};
    for (const component of components) {
      const value = values[component.id];
      if (value === '' || value === null || value === undefined) continue;
      const num = Number(value);
      const max = Number(component.maxScore) || 10;
      if (Number.isNaN(num) || num < 0 || num > max) {
        return `Điểm "${component.componentName}" phải nằm trong khoảng 0 đến ${max}.`;
      }
    }
    const hasAny = components.some((c) => {
      const value = values[c.id];
      return value !== '' && value !== null && value !== undefined;
    });
    if (!hasAny) return 'Vui lòng nhập ít nhất một điểm thành phần trước khi lưu.';
    return '';
  };

  const handleSave = async (row) => {
    const validation = validateRow(row);
    if (validation) {
      toast.error('Điểm chưa hợp lệ', validation);
      return;
    }
    setSavingId(row.enrollmentId);
    try {
      const values = draft[row.enrollmentId];
      const payload = componentMode
        ? {
          componentScores: components.reduce((acc, component) => {
            const value = values?.[component.id];
            if (value !== '' && value !== null && value !== undefined) acc[component.id] = Number(value);
            return acc;
          }, {}),
        }
        : { finalScore: Number(values) };
      await gradeApi.updateGrade(row.enrollmentId, payload);
      const res = await gradebookApi.getSectionDetail(selected);
      setDetail(res.data);
      setDraft(buildDraft(res.data));
      toast.success('Lưu điểm thành công', `Đã cập nhật điểm cho ${row.fullName}.`);
    } catch (err) {
      toast.error('Lưu điểm thất bại', getErrorMessage(err));
    } finally {
      setSavingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Nhập điểm"
        subtitle="Cập nhật điểm cho sinh viên trong lớp học phần được phân công."
        actions={<span className="chip"><UsersIcon size={15} /> {enrollments.length} sinh viên đang học</span>}
      />

      <div className="card" style={{ marginBottom: 18 }}>
        <div className="card-body" style={{ display: 'flex', gap: 14, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Field label="Chọn lớp học phần" className="mb-0" style={{ minWidth: 320, flex: '1 1 320px' }}>
            <select
              className="select"
              value={selected}
              onChange={(e) => {
                setSelected(e.target.value);
                if (e.target.value) setSearchParams({ section: e.target.value });
                else setSearchParams({});
              }}
              disabled={loadingSections}
            >
              <option value="">-- Chọn lớp học phần --</option>
              {sections.map((s) => (
                <option key={s.id} value={s.id}>{s.sectionCode} · {s.course?.courseName} · {s.semester?.semesterName}</option>
              ))}
            </select>
          </Field>
          {currentSection && (
            <div className="flex" style={{ gap: 10, flexWrap: 'wrap', paddingBottom: 1 }}>
              <span className="chip"><BookIcon size={14} /> {currentSection.course?.courseCode} · {currentSection.course?.credits} tín chỉ</span>
              <span className="chip"><ClipboardIcon size={14} /> Lịch: {currentSection.schedule || '—'}</span>
              <StatusBadge status={currentSection.status} />
              {detail && <StatusBadge status={detail.status} />}
            </div>
          )}
        </div>
      </div>

      {!selected ? (
        <div className="card">
          <EmptyState
            icon={<AwardIcon />}
            title="Chưa chọn lớp học phần"
            description="Vui lòng chọn một lớp học phần để xem danh sách sinh viên và nhập điểm."
          />
        </div>
      ) : (
        <div className="card">
          {loadingList ? <LoadingBlock /> : enrollments.length === 0 ? (
            <EmptyState
              icon={<UsersIcon />}
              title="Chưa có sinh viên đăng ký"
              description="Lớp học phần này chưa có sinh viên nào đang theo học."
            />
          ) : (
            <>
              <div className="card-head">
                <div>
                  <h2>Danh sách sinh viên</h2>
                  <p>
                    {componentMode
                      ? `Nhập điểm theo ${components.length} thành phần (tổng trọng số ${Number(totalWeight.toFixed(2))}%)`
                      : 'Nhập điểm cuối kỳ trong thang điểm 0 – 10'}
                  </p>
                </div>
                <Alert tone="info" style={{ marginBottom: 0, padding: '8px 12px' }}>
                  {editable
                    ? 'Chỉ sinh viên có trạng thái Đã đăng ký được hiển thị.'
                    : 'Bảng điểm đã khoá/duyệt/công bố nên không thể chỉnh sửa.'}
                </Alert>
              </div>

              {!editable && (
                <div style={{ padding: '0 20px 16px' }}>
                  <Alert tone="error">
                    <strong><LockIcon size={14} /> Bảng điểm không ở trạng thái mở.</strong>
                    {' '}Vui lòng liên hệ quản trị viên để mở khoá nếu cần chỉnh sửa điểm.
                  </Alert>
                </div>
              )}

              {componentMode && Math.abs(totalWeight - 100) > 0.001 && (
                <div style={{ padding: '0 20px 16px' }}>
                  <Alert tone="error">Tổng trọng số thành phần hiện tại là {Number(totalWeight.toFixed(2))}%, chưa bằng 100%. Vui lòng báo quản trị viên cấu hình lại.</Alert>
                </div>
              )}

              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>STT</th>
                      <th>Sinh viên</th>
                      {componentMode ? components.map((component) => (
                        <th key={component.id}>
                          {component.componentName}
                          <div className="cell-muted" style={{ fontWeight: 500, textTransform: 'none', letterSpacing: 0 }}>
                            {component.weight}% · tối đa {formatScore(component.maxScore)}
                          </div>
                        </th>
                      )) : <th>Điểm cuối kỳ</th>}
                      <th>Điểm tổng kết</th>
                      <th>Xếp loại</th>
                      <th style={{ textAlign: 'right' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row, index) => {
                      const draftRow = draft[row.enrollmentId];
                      const preview = componentMode ? previewFinal(detail, draftRow) : Number(draftRow);
                      const finalValue = preview === null || Number.isNaN(preview) ? row.finalScore : preview;
                      const gl = gradeLabel(finalValue);
                      return (
                        <tr key={row.enrollmentId}>
                          <td>{(page - 1) * PAGE_SIZE + index + 1}</td>
                          <td>
                            <div className="cell-strong">{row.fullName || '—'}</div>
                            <div className="cell-muted">{row.studentCode || '—'} · {row.email || '—'}</div>
                            <div className="cell-muted">
                              {row.complete ? <span className="badge badge-green">Đã đủ điểm</span> : <span className="badge badge-amber">Chưa đủ điểm</span>}
                            </div>
                          </td>
                          {componentMode ? components.map((component) => (
                            <td key={component.id}>
                              <input
                                className="input"
                                type="number"
                                min="0"
                                max={component.maxScore || 10}
                                step="0.01"
                                value={draftRow?.[component.id] ?? ''}
                                onChange={(ev) => handleChange(row.enrollmentId, component.id, ev.target.value)}
                                style={{ width: 92 }}
                                placeholder="0.00"
                                disabled={!editable || savingId === row.enrollmentId}
                              />
                            </td>
                          )) : (
                            <td>
                              <input
                                className="input"
                                type="number"
                                min="0"
                                max="10"
                                step="0.01"
                                value={draftRow ?? ''}
                                onChange={(ev) => handleChange(row.enrollmentId, null, ev.target.value)}
                                style={{ width: 110 }}
                                placeholder="0.00"
                                disabled={!editable || savingId === row.enrollmentId}
                              />
                            </td>
                          )}
                          <td><strong>{formatScore(finalValue)}</strong></td>
                          <td><span className={`badge badge-${gl.tone}`}>{gl.label}</span></td>
                          <td>
                            <div className="row-actions">
                              <button
                                className="btn btn-primary btn-sm"
                                disabled={!editable || savingId === row.enrollmentId}
                                onClick={() => handleSave(row)}
                              >
                                {savingId === row.enrollmentId ? 'Đang lưu...' : <><SaveIcon size={14} /> Lưu</>}
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} pageSize={PAGE_SIZE} totalItems={enrollments.length} onPageChange={setPage} />
            </>
          )}
        </div>
      )}
    </>
  );
};

export default LecturerGrades;
