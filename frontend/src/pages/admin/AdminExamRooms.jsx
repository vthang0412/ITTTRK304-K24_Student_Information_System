import React, { useEffect, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import { examRoomApi } from '../../api';
import { Alert, EmptyState, Field, LoadingBlock, Modal, PageHeader, Pagination, SearchInput, StatusBadge } from '../../components/UI';
import { LayersIcon, PlusIcon } from '../../components/Icons';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/format';
import './AdminExamRooms.css';

const EMPTY_ROOM = { roomCode: '', roomName: '', building: '', capacity: '', description: '', status: 'ACTIVE' };

const AdminExamRooms = () => {
  const toast = useToast();
  const [rooms, setRooms] = useState([]);
  const [filters, setFilters] = useState({ keyword: '', building: '', status: '' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  const [loadedRequestKey, setLoadedRequestKey] = useState('');
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_ROOM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const requestKey = JSON.stringify([filters, page, pageSize, reloadKey]);
  const loading = requestKey !== loadedRequestKey;

  useEffect(() => {
    let active = true;
    examRoomApi.getAll({
      keyword: filters.keyword.trim() || undefined,
      building: filters.building.trim() || undefined,
      status: filters.status || undefined,
      page: page - 1,
      size: pageSize,
      sort: 'id,desc',
    }).then(({ data }) => {
      if (!active) return;
      setRooms(Array.isArray(data?.content) ? data.content : []);
      setTotalItems(Number(data?.totalElements || 0));
      setLoadError('');
      setLoadedRequestKey(requestKey);
    }).catch((error) => {
      if (!active) return;
      setRooms([]);
      setTotalItems(0);
      setLoadError(getErrorMessage(error));
      setLoadedRequestKey(requestKey);
      toast.error('Không tải được danh sách phòng thi', getErrorMessage(error));
    });
    return () => { active = false; };
  }, [filters, page, pageSize, reloadKey, requestKey, toast]);

  const updateFilter = (key, value) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(1);
  };
  const refresh = () => setReloadKey((value) => value + 1);
  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_ROOM);
    setFormErrors({});
    setFormOpen(true);
  };
  const openEdit = (room) => {
    setEditing(room);
    setForm({
      roomCode: room.roomCode || '',
      roomName: room.roomName || '',
      building: room.building || '',
      capacity: room.capacity ?? '',
      description: room.description || '',
      status: room.status || 'ACTIVE',
    });
    setFormErrors({});
    setFormOpen(true);
  };
  const validate = () => {
    const errors = {};
    if (!form.roomCode.trim()) errors.roomCode = 'Mã phòng là bắt buộc.';
    if (!form.roomName.trim()) errors.roomName = 'Tên phòng là bắt buộc.';
    if (!Number.isInteger(Number(form.capacity)) || Number(form.capacity) < 1) errors.capacity = 'Sức chứa phải là số nguyên dương.';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };
  const saveRoom = async (event) => {
    event.preventDefault();
    if (savingRef.current || !validate()) return;
    savingRef.current = true;
    setSaving(true);
    const payload = {
      roomCode: form.roomCode.trim(),
      roomName: form.roomName.trim(),
      building: form.building.trim() || null,
      capacity: Number(form.capacity),
      description: form.description.trim() || null,
      status: form.status,
    };
    try {
      if (editing) {
        await examRoomApi.update(editing.id, payload);
        toast.success('Cập nhật phòng thi thành công');
      } else {
        await examRoomApi.create(payload);
        toast.success('Thêm phòng thi thành công');
      }
      setFormOpen(false);
      refresh();
    } catch (error) {
      toast.error('Không thể lưu phòng thi', getErrorMessage(error));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };
  const changeStatus = async (room) => {
    const nextStatus = room.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (nextStatus === 'INACTIVE' && room.inUse) {
      toast.error('Không thể ngừng sử dụng phòng đang được sử dụng', 'Hãy xử lý các lịch thi liên quan trước.');
      return;
    }
    const result = await Swal.fire({
      title: nextStatus === 'ACTIVE' ? 'Kích hoạt phòng thi?' : 'Ngừng sử dụng phòng thi?',
      text: `${room.roomCode} - ${room.roomName}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: nextStatus === 'ACTIVE' ? 'Kích hoạt' : 'Ngừng sử dụng',
      cancelButtonText: 'Quay lại',
      reverseButtons: true,
    });
    if (!result.isConfirmed) return;
    try {
      await examRoomApi.setStatus(room.id, nextStatus);
      toast.success(nextStatus === 'ACTIVE' ? 'Đã kích hoạt phòng' : 'Đã ngừng sử dụng phòng');
      refresh();
    } catch (error) {
      toast.error('Không thể cập nhật trạng thái phòng', getErrorMessage(error));
    }
  };
  const deleteRoom = async (room) => {
    if (room.inUse) {
      toast.error('Không thể xóa phòng đang được sử dụng', 'Hãy xử lý các lịch thi liên quan trước.');
      return;
    }
    const result = await Swal.fire({
      title: 'Xóa phòng thi?',
      text: `Phòng ${room.roomCode} - ${room.roomName} sẽ bị xóa.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Xóa phòng',
      cancelButtonText: 'Quay lại',
      reverseButtons: true,
    });
    if (!result.isConfirmed) return;
    try {
      await examRoomApi.delete(room.id);
      toast.success('Đã xóa phòng thi');
      refresh();
    } catch (error) {
      toast.error('Không thể xóa phòng thi', getErrorMessage(error));
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý phòng thi"
        subtitle="Quản lý địa điểm, sức chứa và trạng thái sử dụng phòng thi."
        actions={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm phòng thi</button>}
      />
      <div className="toolbar admin-exam-rooms-filters">
        <SearchInput value={filters.keyword} onChange={(value) => updateFilter('keyword', value)} placeholder="Tìm mã phòng hoặc tên phòng..." />
        <input className="input exam-filter-building" value={filters.building} onChange={(event) => updateFilter('building', event.target.value)} placeholder="Lọc theo tòa nhà" aria-label="Lọc theo tòa nhà" />
        <select className="select" value={filters.status} onChange={(event) => updateFilter('status', event.target.value)}>
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang sử dụng</option>
          <option value="INACTIVE">Ngừng sử dụng</option>
        </select>
      </div>
      <section className="card">
        {loading ? <LoadingBlock /> : loadError ? (
          <Alert tone="error">
            {loadError}
            <button className="btn btn-sm btn-secondary" style={{ marginLeft: 12 }} onClick={refresh}>Thử lại</button>
          </Alert>
        ) : rooms.length === 0 ? (
          <EmptyState
            icon={<LayersIcon />}
            title="Chưa có phòng thi phù hợp"
            description="Thêm phòng thi mới hoặc điều chỉnh các bộ lọc."
            action={<button className="btn btn-primary" onClick={openCreate}><PlusIcon /> Thêm phòng thi</button>}
          />
        ) : (
          <>
            <div className="table-wrap admin-exam-rooms-table-wrap">
              <table className="table admin-exam-rooms-table">
                <colgroup>
                  <col className="exam-room-col-code" />
                  <col className="exam-room-col-name" />
                  <col className="exam-room-col-building" />
                  <col className="exam-room-col-capacity" />
                  <col className="exam-room-col-status" />
                  <col className="exam-room-col-usage" />
                  <col className="exam-room-col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th className="exam-room-header-code">Mã phòng</th>
                    <th className="exam-room-header-name">Tên phòng</th>
                    <th className="exam-room-header-building">Tòa nhà</th>
                    <th className="exam-room-header-capacity">Sức chứa</th>
                    <th className="exam-room-header-status">Trạng thái</th>
                    <th className="exam-room-header-usage">Đang sử dụng</th>
                    <th className="exam-room-header-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((room) => (
                    <tr key={room.id}>
                      <td className="exam-room-cell-code"><strong>{room.roomCode}</strong></td>
                      <td className="exam-room-cell-name">{room.roomName}</td>
                      <td className="exam-room-cell-building">{room.building || '—'}</td>
                      <td className="exam-room-cell-capacity">{room.capacity}</td>
                      <td className="exam-room-cell-status"><StatusBadge status={room.status} /></td>
                      <td className="exam-room-cell-usage">{room.inUse ? <span className="chip chip-warning">Có lịch thi liên quan</span> : <span className="muted">Chưa sử dụng</span>}</td>
                      <td className="exam-room-cell-actions">
                        <div className="exam-row-actions">
                          <button className="btn btn-sm btn-secondary exam-room-action-edit" onClick={() => openEdit(room)}>Sửa</button>
                          <button
                            className="btn btn-sm btn-secondary exam-room-action-status"
                            disabled={room.status === 'ACTIVE' && room.inUse}
                            onClick={() => changeStatus(room)}
                          >
                            {room.status === 'ACTIVE' ? 'Ngừng dùng' : 'Kích hoạt'}
                          </button>
                          <button className="btn btn-sm btn-danger exam-room-action-delete" disabled={room.inUse} onClick={() => deleteRoom(room)}>Xóa</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(value) => { setPageSize(value); setPage(1); }}
            />
          </>
        )}
      </section>

      <Modal
        open={formOpen}
        onClose={() => { if (!saving) setFormOpen(false); }}
        title={editing ? 'Chỉnh sửa phòng thi' : 'Thêm phòng thi'}
        size="md"
        footer={(
          <>
            <button className="btn btn-secondary" type="button" onClick={() => setFormOpen(false)} disabled={saving}>Đóng</button>
            <button className="btn btn-primary" type="submit" form="exam-room-form" disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu phòng thi'}</button>
          </>
        )}
      >
        {editing?.inUse && <Alert tone="info">Phòng này đang được sử dụng trong lịch thi. Không thể ngừng sử dụng hoặc xóa cho đến khi xử lý các lịch thi liên quan.</Alert>}
        <form id="exam-room-form" onSubmit={saveRoom}>
          <div className="form-grid">
            <Field label="Mã phòng" required error={formErrors.roomCode}>
              <input className="input" value={form.roomCode} onChange={(event) => setForm((value) => ({ ...value, roomCode: event.target.value }))} />
            </Field>
            <Field label="Tên phòng" required error={formErrors.roomName}>
              <input className="input" value={form.roomName} onChange={(event) => setForm((value) => ({ ...value, roomName: event.target.value }))} />
            </Field>
            <Field label="Tòa nhà">
              <input className="input" value={form.building} onChange={(event) => setForm((value) => ({ ...value, building: event.target.value }))} />
            </Field>
            <Field label="Sức chứa" required error={formErrors.capacity}>
              <input className="input" type="number" min="1" step="1" value={form.capacity} onChange={(event) => setForm((value) => ({ ...value, capacity: event.target.value }))} />
            </Field>
            <Field label="Trạng thái">
              <select className="select" value={form.status} onChange={(event) => setForm((value) => ({ ...value, status: event.target.value }))}>
                <option value="ACTIVE">Đang sử dụng</option>
                <option value="INACTIVE" disabled={Boolean(editing?.inUse)}>Ngừng sử dụng</option>
              </select>
            </Field>
            <Field label="Mô tả" className="full">
              <textarea className="input" rows="3" value={form.description} onChange={(event) => setForm((value) => ({ ...value, description: event.target.value }))} />
            </Field>
          </div>
        </form>
      </Modal>
    </>
  );
};

export default AdminExamRooms;
