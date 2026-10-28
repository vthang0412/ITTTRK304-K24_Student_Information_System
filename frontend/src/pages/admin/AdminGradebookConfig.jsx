  import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { courseSectionApi, gradebookApi } from '../../api';
import { useToast } from '../../context/ToastContext';
import { Alert, Field, LoadingBlock, PageHeader, StatusBadge } from '../../components/UI';
import { ArrowLeftIcon, PlusIcon, SaveIcon, TrashIcon } from '../../components/Icons';
import { getErrorMessage } from '../../utils/format';

const COMPONENT_TYPES = [
  { value: 'ATTENDANCE', label: 'Chuyên cần' },
  { value: 'ASSIGNMENT', label: 'Bài tập' },
  { value: 'MIDTERM', label: 'Giữa kỳ' },
  { value: 'FINAL', label: 'Cuối kỳ' },
  { value: 'PROJECT', label: 'Đồ án' },
  { value: 'OTHER', label: 'Khác' },
];

const newRow = (index) => ({
  key: `new-${Date.now()}-${index}`,
  componentName: '',
  weight: '',
  maxScore: '10',
  componentType: 'ASSIGNMENT',
  displayOrder: index + 1,
  required: true,
});

const toRows = (components = []) => components.map((component) => ({
  key: `c-${component.id}`,
  id: component.id,
  componentName: component.componentName,
  weight: String(component.weight ?? ''),
  maxScore: String(component.maxScore ?? '10'),
  componentType: component.componentType || 'OTHER',
  displayOrder: component.displayOrder ?? 0,
  required: component.required !== false,
}));

const AdminGradebookConfig = () => {
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [sections, setSections] = useState([]);
  const [loadingSections, setLoadingSections] = useState(true);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [detail, setDetail] = useState(null);
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');

  const sectionId = searchParams.get('section') || '';

  useEffect(() => {
    (async () => {
      try {
        const res = await courseSectionApi.getAll();
        setSections(res.data || []);
      } catch (err) {
        toast.error('Không tải được danh sách lớp học phần', getErrorMessage(err));
      } finally {
        setLoadingSections(false);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!sectionId) {
      setDetail(null);
      setRows([]);
      return;
    }
    (async () => {
      setLoading(true);
      setError('');
      try {
        const res = await gradebookApi.getSectionDetail(sectionId);
        setDetail(res.data);
        setRows(toRows(res.data.components));
      } catch (err) {
        setError(getErrorMessage(err, 'Không tải được cấu hình thành phần điểm.'));
        setDetail(null);
        setRows([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [sectionId]);

  const totalWeight = useMemo(
    () => rows.reduce((sum, row) => sum + (Number(row.weight) || 0), 0),
    [rows],
  );

  const editable = detail?.status === 'OPEN';

  const updateRow = (key, field, value) => {
    setRows((prev) => prev.map((row) => (row.key === key ? { ...row, [field]: value } : row)));
  };

  const addRow = () => {
    setRows((prev) => [...prev, newRow(prev.length)]);
  };

  const removeRow = (key) => {
    setRows((prev) => prev.filter((row) => row.key !== key));
  };

  const applyTemplate = () => {
    setRows([
      { ...newRow(0), componentName: 'Chuyên cần', weight: '10', componentType: 'ATTENDANCE' },
      { ...newRow(1), componentName: 'Giữa kỳ', weight: '20', componentType: 'MIDTERM' },
      { ...newRow(2), componentName: 'Cuối kỳ', weight: '70', componentType: 'FINAL' },
    ]);
  };

  const validate = () => {
    if (rows.length === 0) return 'Cần cấu hình ít nhất một thành phần điểm.';
    const names = new Set();
    for (const row of rows) {
      const name = row.componentName.trim();
      if (!name) return 'Tên thành phần điểm không được để trống.';
      if (names.has(name.toLowerCase())) return `Thành phần "${name}" bị trùng tên.`;
      names.add(name.toLowerCase());
      const weight = Number(row.weight);
      if (!Number.isFinite(weight) || weight <= 0 || weight > 100) {
        return `Trọng số của "${name}" phải lớn hơn 0 và không vượt quá 100.`;
      }
      const maxScore = Number(row.maxScore);
      if (!Number.isFinite(maxScore) || maxScore <= 0 || maxScore > 100) {
        return `Điểm tối đa của "${name}" phải lớn hơn 0 và không vượt quá 100.`;
      }
    }
    if (Math.abs(totalWeight - 100) > 0.001) return 'Tổng trọng số các thành phần phải bằng 100%.';
    return '';
  };

  const handleSave = async () => {
    const validation = validate();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = rows.map((row, index) => ({
        componentName: row.componentName.trim(),
        weight: Number(row.weight),
        maxScore: Number(row.maxScore),
        componentType: row.componentType,
        displayOrder: index + 1,
        required: true,
      }));
      await gradebookApi.updateComponents(sectionId, payload);
      const res = await gradebookApi.getSectionDetail(sectionId);
      setDetail(res.data);
      setRows(toRows(res.data.components));
      toast.success('Lưu cấu hình thành công', 'Cấu hình thành phần điểm đã được cập nhật.');
    } catch (err) {
      const message = getErrorMessage(err, 'Không lưu được cấu hình thành phần điểm.');
      setError(message);
      toast.error('Lưu cấu hình thất bại', message);
    } finally {
      setSaving(false);
    }
  };

  const selectedSection = sections.find((s) => String(s.id) === String(sectionId));

  return (
    <>
      <PageHeader
        title="Cấu hình thành phần điểm"
        subtitle="Thiết lập trọng số và điểm tối đa cho từng thành phần. Tổng trọng số phải bằng 100%."
        actions={<Link to="/admin/gradebooks" className="btn btn-secondary"><ArrowLeftIcon /> Quay lại</Link>}
      />

      <div className="toolbar">
        <select
          className="select"
          style={{ minWidth: 360 }}
          value={sectionId}
          onChange={(e) => {
            const value = e.target.value;
            setSearchParams(value ? { section: value } : {});
          }}
          disabled={loadingSections}
        >
          <option value="">Chọn lớp học phần</option>
          {sections.map((section) => (
            <option key={section.id} value={section.id}>
              {section.sectionCode} · {section.course?.courseName || section.courseName || ''} · {section.semester?.semesterName || section.semesterName || ''}
            </option>
          ))}
        </select>
        {detail && (
          <>
            <span className="chip">
              {detail.gradingMode === 'COMPONENT' ? 'Đang dùng điểm thành phần' : 'Đang dùng điểm cuối kỳ'}
            </span>
            <StatusBadge status={detail.status} />
          </>
        )}
        <div className="toolbar-spacer" />
        {sectionId && detail && (
          <span className={`chip ${Math.abs(totalWeight - 100) < 0.001 ? 'chip-success' : 'chip-warning'}`}>
            Tổng trọng số: {Number(totalWeight.toFixed(2))}%
          </span>
        )}
      </div>

      {error && <Alert tone="error" className="mb-16">{error}</Alert>}

      {!sectionId ? (
        <div className="card">
          <div className="empty-state">
            <h3>Chọn lớp học phần</h3>
            <p>Chọn một lớp học phần để xem và cấu hình các thành phần điểm.</p>
          </div>
        </div>
      ) : loading ? (
        <div className="card"><LoadingBlock /></div>
      ) : !detail ? null : (
        <div className="card">
          <div className="card-head">
            <div>
              <h3>{selectedSection?.sectionCode || detail.sectionCode} · {selectedSection?.course?.courseName || detail.courseName}</h3>
              <p className="muted small">Giảng viên: {detail.lecturerName || 'Chưa phân công'} · Sinh viên: {detail.enrollments?.length || 0}</p>
            </div>
            <div className="row-actions">
              <button className="btn btn-secondary" onClick={applyTemplate} disabled={!editable || saving}>Mẫu 10/20/70</button>
              <button className="btn btn-secondary" onClick={addRow} disabled={!editable || saving}><PlusIcon /> Thêm thành phần</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={!editable || saving}>
                {saving ? <span className="spinner" /> : <SaveIcon />} Lưu cấu hình
              </button>
            </div>
          </div>

          {!editable && (
            <div style={{ padding: '0 20px' }}>
              <Alert tone="info">
                Bảng điểm đang ở trạng thái {detail.status === 'LOCKED' ? 'đã khoá' : detail.status === 'APPROVED' ? 'đã duyệt' : 'đã công bố'}.
                Cần mở khoá bảng điểm trước khi thay đổi cấu hình thành phần điểm.
              </Alert>
            </div>
          )}

          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ width: 52 }}>#</th>
                  <th>Tên thành phần</th>
                  <th style={{ width: 120 }}>Trọng số (%)</th>
                  <th style={{ width: 120 }}>Điểm tối đa</th>
                  <th style={{ width: 160 }}>Loại</th>
                  <th style={{ width: 60 }} />
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr><td colSpan={6} className="cell-muted" style={{ textAlign: 'center', padding: 24 }}>Chưa có thành phần điểm. Bấm "Thêm thành phần" để bắt đầu.</td></tr>
                ) : rows.map((row, index) => (
                  <tr key={row.key}>
                    <td className="cell-muted">{index + 1}</td>
                    <td>
                      <input
                        className="input"
                        value={row.componentName}
                        onChange={(e) => updateRow(row.key, 'componentName', e.target.value)}
                        placeholder="VD: Giữa kỳ"
                        disabled={!editable || saving}
                      />
                    </td>
                    <td>
                      <input
                        className="input"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        value={row.weight}
                        onChange={(e) => updateRow(row.key, 'weight', e.target.value)}
                        disabled={!editable || saving}
                      />
                    </td>
                    <td>
                      <input
                        className="input"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        value={row.maxScore}
                        onChange={(e) => updateRow(row.key, 'maxScore', e.target.value)}
                        disabled={!editable || saving}
                      />
                    </td>
                    <td>
                      <select
                        className="select"
                        value={row.componentType}
                        onChange={(e) => updateRow(row.key, 'componentType', e.target.value)}
                        disabled={!editable || saving}
                      >
                        {COMPONENT_TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
                      </select>
                    </td>
                    <td>
                      <button
                        className="icon-btn danger"
                        title="Xoá thành phần"
                        onClick={() => removeRow(row.key)}
                        disabled={!editable || saving}
                      >
                        <TrashIcon />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ padding: 20 }}>
            <Field hint="Hệ thống tính điểm tổng kết = tổng(điểm thành phần / điểm tối đa × trọng số). Điểm thành phần được nhập theo thang điểm tối đa của từng thành phần.">
              <span className="muted small">
                Tổng trọng số hiện tại: <strong>{Number(totalWeight.toFixed(2))}%</strong>
                {Math.abs(totalWeight - 100) < 0.001 ? ' · Hợp lệ' : ' · Cần điều chỉnh để bằng 100%'}
              </span>
            </Field>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminGradebookConfig;
