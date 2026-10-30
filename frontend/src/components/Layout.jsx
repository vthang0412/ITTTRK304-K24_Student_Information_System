import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { notificationApi } from '../api';
import {
  AwardIcon, BellIcon, BookIcon, CalendarIcon, ChevronRightIcon,
  ClipboardIcon, DashboardIcon, LayersIcon, LogoutIcon, MenuIcon, PanelIcon,
  StudentsIcon, UserIcon, UsersIcon,
} from './Icons';

const MENUS = {
  STUDENT: [
    { label: 'Tổng quan', to: '/dashboard', icon: DashboardIcon, section: 'Không gian học tập' },
    { label: 'Hồ sơ cá nhân', to: '/student/profile', icon: UserIcon },
    { label: 'Đăng ký học phần', to: '/student/courses', icon: BookIcon },
    { label: 'Học phần đã đăng ký', to: '/student/enrollments', icon: ClipboardIcon },
    { label: 'Kết quả học tập', to: '/student/grades', icon: AwardIcon },
    { label: 'Thông báo', to: '/student/notifications', icon: BellIcon },
  ],
  LECTURER: [
    { label: 'Tổng quan', to: '/dashboard', icon: DashboardIcon, section: 'Không gian giảng dạy' },
    { label: 'Hồ sơ cá nhân', to: '/lecturer/profile', icon: UserIcon },
    { label: 'Lớp học phần', to: '/lecturer/sections', icon: LayersIcon },
    { label: 'Nhập điểm', to: '/lecturer/grades', icon: ClipboardIcon },
  ],
  ADMIN: [
    { label: 'Tổng quan', to: '/dashboard', icon: DashboardIcon, section: 'Quản trị hệ thống' },
    { label: 'Hồ sơ cá nhân', to: '/admin/profile', icon: UserIcon },
    { label: 'Sinh viên', to: '/admin/students', icon: StudentsIcon },
    { label: 'Giảng viên', to: '/admin/lecturers', icon: UsersIcon },
    { label: 'Môn học', to: '/admin/courses', icon: BookIcon },
    { label: 'Học kỳ', to: '/admin/semesters', icon: CalendarIcon },
    { label: 'Lớp học phần', to: '/admin/sections', icon: LayersIcon },
    { label: 'Bảng điểm', to: '/admin/gradebooks', icon: AwardIcon },
    { label: 'Thông báo', to: '/admin/notifications', icon: BellIcon },
  ],
};
const TITLES = {
  '/dashboard': 'Tổng quan',
  '/student/profile': 'Hồ sơ cá nhân',
  '/student/courses': 'Đăng ký học phần',
  '/student/enrollments': 'Học phần đã đăng ký',
  '/student/grades': 'Kết quả học tập',
  '/student/notifications': 'Thông báo',
  '/lecturer/profile': 'Hồ sơ cá nhân',
  '/lecturer/sections': 'Lớp học phần',
  '/lecturer/grades': 'Nhập điểm',
  '/admin/profile': 'Hồ sơ cá nhân',
  '/admin/students': 'Quản lý sinh viên',
  '/admin/lecturers': 'Quản lý giảng viên',
  '/admin/courses': 'Quản lý môn học',
  '/admin/semesters': 'Quản lý học kỳ',
  '/admin/sections': 'Quản lý lớp học phần',
  '/admin/gradebooks': 'Quản lý bảng điểm',
  '/admin/gradebook-config': 'Cấu hình thành phần điểm',
  '/admin/notifications': 'Quản lý thông báo',
};

const initials = (name = '') => {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return 'U';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
};

const roleLabel = (role) => ({ ADMIN: 'Quản trị viên', LECTURER: 'Giảng viên', STUDENT: 'Sinh viên' }[role] || role);

const Layout = ({ children }) => {
  const { user, logout, refreshProfile } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('sidebar-collapsed') === '1');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [today] = useState(() => new Date());
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    refreshProfile();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* Poll số thông báo chưa đọc (chỉ dành cho STUDENT và ADMIN) */
  useEffect(() => {
    if (!user?.role || user.role === 'LECTURER') return undefined;
    const fetchUnread = async () => {
      try {
        const res = await notificationApi.getUnreadCount();
        setUnreadCount(res.data?.unreadCount || 0);
      } catch { /* imặng nếu lỗi */ }
    };
    fetchUnread();
    const id = setInterval(fetchUnread, 60_000); // cập nhật mỗi 1 phút
    return () => clearInterval(id);
  }, [user?.role, location.pathname]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const menu = MENUS[user?.role] || MENUS.STUDENT;
  const displayName = user?.fullName || user?.username || 'Người dùng';
  const currentTitle = TITLES[location.pathname] || 'Trang';
  const role = roleLabel(user?.role);

  const grouped = useMemo(() => {
    const groups = [];
    menu.forEach((item) => {
      const section = item.section || groups[groups.length - 1]?.label || 'Chức năng';
      let group = groups.find((g) => g.label === section);
      if (!group) {
        group = { label: section, items: [] };
        groups.push(group);
      }
      group.items.push(item);
    });
    return groups;
  }, [menu]);

  const toggleCollapse = () => {
    setCollapsed((prev) => {
      localStorage.setItem('sidebar-collapsed', prev ? '0' : '1');
      return !prev;
    });
  };

  const handleLogout = async () => {
    const result = await Swal.fire({
      title: 'Xác nhận đăng xuất?',
      text: 'Bạn có chắc chắn muốn đăng xuất khỏi hệ thống không?',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Xác nhận đăng xuất',
      cancelButtonText: 'Hủy',
      reverseButtons: true,
      customClass: {
        popup: 'swal-auth-popup',
        title: 'swal-auth-title',
        htmlContainer: 'swal-auth-text',
        confirmButton: 'swal-auth-confirm',
        cancelButton: 'swal-auth-cancel',
        icon: 'swal-auth-icon',
      },
      buttonsStyling: false,
      allowOutsideClick: false,
      allowEscapeKey: false,
      background: '#ffffff',
      color: '#0f172a',
      width: 420,
      didOpen: () => {
        const popup = document.querySelector('.swal2-popup');
        if (popup) {
          popup.style.borderRadius = '16px';
          popup.style.boxShadow = '0 20px 50px rgba(15, 23, 42, 0.18)';
        }
      },
    });

    if (!result.isConfirmed) return;

    logout();
    await Swal.fire({
      icon: 'success',
      title: 'Đăng xuất thành công',
      text: 'Bạn đã được đăng xuất khỏi hệ thống.',
      timer: 1500,
      showConfirmButton: false,
      customClass: {
        popup: 'swal-auth-popup',
        title: 'swal-auth-title',
        htmlContainer: 'swal-auth-text',
        icon: 'swal-auth-icon',
      },
      background: '#ffffff',
      color: '#0f172a',
      width: 420,
      allowOutsideClick: false,
      allowEscapeKey: false,
    });

    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <div
        className={`sidebar-overlay${mobileOpen ? ' show' : ''}`}
        onClick={() => setMobileOpen(false)}
      />

      <aside className={`sidebar${collapsed ? ' is-collapsed' : ''}${mobileOpen ? ' mobile-open' : ''}`}>
        <div className="sidebar-brand">
          <div className="brand-logo">SIS</div>
          <div className="brand-text">
            <strong>Student Information</strong>
            <span>Hệ thống quản lý đào tạo</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          {grouped.map((group) => (
            <div className="nav-section" key={group.label}>
              <div className="nav-label">{group.label}</div>
              {group.items.map((item) => {
                const Icon = item.icon;
                const isNotif = item.to.endsWith('/notifications');
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                    title={collapsed ? item.label : undefined}
                  >
                    <span style={{ position: 'relative', display: 'inline-flex' }}>
                      <Icon />
                      {isNotif && unreadCount > 0 && (
                        <span style={{
                          position: 'absolute', top: -4, right: -6,
                          minWidth: 14, height: 14,
                          background: '#ef4444', color: '#fff',
                          borderRadius: 7, fontSize: 9, fontWeight: 700,
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          padding: '0 2px', lineHeight: 1,
                        }}>
                          {unreadCount > 99 ? '99+' : unreadCount}
                        </span>
                      )}
                    </span>
                    <span>{item.label}</span>
                    {isNotif && unreadCount > 0 && !collapsed && (
                      <span style={{
                        marginLeft: 'auto', background: '#ef4444', color: '#fff',
                        borderRadius: 10, fontSize: 11, fontWeight: 700,
                        padding: '1px 7px',
                      }}>
                        {unreadCount > 99 ? '99+' : unreadCount}
                      </span>
                    )}
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="avatar">{initials(displayName)}</div>
            <div className="sidebar-user-meta">
              <strong>{displayName}</strong>
              <span>{role}</span>
            </div>
          </div>
          <button className="btn btn-danger btn-block btn-sm btn-logout" onClick={handleLogout}>
            <LogoutIcon size={16} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      <div className={`main-shell${collapsed ? ' sidebar-collapsed' : ''}`}>
        <header className="topbar">
          <div className="topbar-left">
            <button className="icon-btn hide-mobile" onClick={toggleCollapse} aria-label="Thu gọn menu">
              <PanelIcon />
            </button>
            <button className="icon-btn show-mobile" onClick={() => setMobileOpen(true)} aria-label="Mở menu">
              <MenuIcon />
            </button>
            <div className="breadcrumb">
              <span>Trang chủ</span>
              <ChevronRightIcon />
              <b>{currentTitle}</b>
            </div>
          </div>

          <div className="topbar-right">
            <span className="topbar-date">
              {today.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })}
            </span>
            <button
              className="icon-btn"
              aria-label="Thông báo"
              onClick={() => navigate(user?.role === 'ADMIN' ? '/admin/notifications' : '/student/notifications')}
              style={{ position: 'relative' }}
            >
              <BellIcon />
              {unreadCount > 0 && (
                <span style={{
                  position: 'absolute', top: 2, right: 2,
                  minWidth: 16, height: 16,
                  background: '#ef4444', color: '#fff',
                  borderRadius: 8, fontSize: 10, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  padding: '0 3px', lineHeight: 1,
                  border: '1.5px solid var(--surface)',
                  pointerEvents: 'none',
                }}>
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </button>
            <button className="topbar-user-btn" onClick={() => navigate(user?.role === 'ADMIN' ? '/admin/profile' : user?.role === 'LECTURER' ? '/lecturer/profile' : '/student/profile')}>
              <div className="topbar-user">
                <div className="topbar-user-meta">
                  <strong>{displayName}</strong>
                  <span>{role}</span>
                </div>
                <div className="avatar">{initials(displayName)}</div>
              </div>
            </button>
          </div>
        </header>

        <main className="page-content">{children}</main>
      </div>
    </div>
  );
};

export default Layout;
