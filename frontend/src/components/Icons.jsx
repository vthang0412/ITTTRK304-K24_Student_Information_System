import React from 'react';

const Svg = ({ children, size = 20, className = '', ...rest }) => (
  <svg
    className={className}
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    {...rest}
  >
    {children}
  </svg>
);

export const DashboardIcon = (p) => (
  <Svg {...p}><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></Svg>
);
export const StudentsIcon = (p) => (
  <Svg {...p}><path d="M22 10 12 5 2 10l10 5 10-5Z" /><path d="M6 12v5c0 1.7 2.7 3 6 3s6-1.3 6-3v-5" /><path d="M22 10v6" /></Svg>
);
export const UserIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="8" r="4" /><path d="M4 21v-1a7 7 0 0 1 7-7h2a7 7 0 0 1 7 7v1" /></Svg>
);
export const UsersIcon = (p) => (
  <Svg {...p}><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></Svg>
);
export const BookIcon = (p) => (
  <Svg {...p}><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" /></Svg>
);
export const CalendarIcon = (p) => (
  <Svg {...p}><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></Svg>
);
export const LayersIcon = (p) => (
  <Svg {...p}><path d="m12 2 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5" /><path d="m3 17 9 5 9-5" /></Svg>
);
export const ClipboardIcon = (p) => (
  <Svg {...p}><rect x="8" y="2" width="8" height="4" rx="1" /><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" /><path d="M9 12h6M9 16h4" /></Svg>
);
export const AwardIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="8" r="6" /><path d="m8.2 13.5-1.5 8L12 19l5.3 2.5-1.5-8" /></Svg>
);
export const LogoutIcon = (p) => (
  <Svg {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></Svg>
);
export const MenuIcon = (p) => (
  <Svg {...p}><path d="M3 6h18M3 12h18M3 18h18" /></Svg>
);
export const PanelIcon = (p) => (
  <Svg {...p}><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M9 3v18" /></Svg>
);
export const ChevronRightIcon = (p) => (
  <Svg {...p}><path d="m9 18 6-6-6-6" /></Svg>
);
export const ChevronLeftIcon = (p) => (
  <Svg {...p}><path d="m15 18-6-6 6-6" /></Svg>
);
export const SearchIcon = (p) => (
  <Svg {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></Svg>
);
export const PlusIcon = (p) => (
  <Svg {...p}><path d="M12 5v14M5 12h14" /></Svg>
);
export const PencilIcon = (p) => (
  <Svg {...p}><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></Svg>
);
export const TrashIcon = (p) => (
  <Svg {...p}><path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M10 11v6M14 11v6" /></Svg>
);
export const XIcon = (p) => (
  <Svg {...p}><path d="M18 6 6 18M6 6l12 12" /></Svg>
);
export const CheckIcon = (p) => (
  <Svg {...p}><path d="M20 6 9 17l-5-5" /></Svg>
);
export const CheckCircleIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="12" r="9" /><path d="m8.5 12.5 2.5 2.5 4.5-5" /></Svg>
);
export const AlertCircleIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16h.01" /></Svg>
);
export const InfoIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></Svg>
);
export const InboxIcon = (p) => (
  <Svg {...p}><path d="M22 12h-6l-2 3h-4l-2-3H2" /><path d="M5.5 5.5 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.5A2 2 0 0 0 16.7 4H7.3a2 2 0 0 0-1.8 1.5Z" /></Svg>
);
export const EyeIcon = (p) => (
  <Svg {...p}><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></Svg>
);
export const EyeOffIcon = (p) => (
  <Svg {...p}><path d="M9.9 4.24A9.1 9.1 0 0 1 12 4c6.5 0 10 7 10 7a17.6 17.6 0 0 1-2.6 3.5M6.6 6.6A17.6 17.6 0 0 0 2 11s3.5 7 10 7a9.1 9.1 0 0 0 3.4-.65" /><path d="M14.1 14.1a3 3 0 1 1-4.2-4.2" /><path d="m2 2 20 20" /></Svg>
);
export const LockIcon = (p) => (
  <Svg {...p}><rect x="3" y="11" width="18" height="10" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></Svg>
);
export const MailIcon = (p) => (
  <Svg {...p}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></Svg>
);
export const ArrowRightIcon = (p) => (
  <Svg {...p}><path d="M5 12h14M13 6l6 6-6 6" /></Svg>
);
export const ArrowLeftIcon = (p) => (
  <Svg {...p}><path d="M19 12H5M11 18l-6-6 6-6" /></Svg>
);
export const BellIcon = (p) => (
  <Svg {...p}><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" /></Svg>
);
export const ClockIcon = (p) => (
  <Svg {...p}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Svg>
);
export const PinIcon = (p) => (
  <Svg {...p}><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" /></Svg>
);
export const TrendingIcon = (p) => (
  <Svg {...p}><path d="m3 17 6-6 4 4 8-8" /><path d="M15 7h6v6" /></Svg>
);
export const ShieldIcon = (p) => (
  <Svg {...p}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" /><path d="m9 12 2 2 4-4" /></Svg>
);
export const SparklesIcon = (p) => (
  <Svg {...p}><path d="m12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9Z" /><path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9Z" /></Svg>
);
export const RefreshIcon = (p) => (
  <Svg {...p}><path d="M21 12a9 9 0 1 1-2.6-6.4" /><path d="M21 3v6h-6" /></Svg>
);
export const SaveIcon = (p) => (
  <Svg {...p}><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" /><path d="M17 21v-8H7v8M7 3v5h8" /></Svg>
);
export const FilterIcon = (p) => (
  <Svg {...p}><path d="M22 3H2l8 9.5V19l4 2v-8.5L22 3Z" /></Svg>
);
export const HomeIcon = (p) => (
  <Svg {...p}><path d="m3 10 9-7 9 7v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" /><path d="M9 21v-7h6v7" /></Svg>
);
export const FileTextIcon = (p) => (
  <Svg {...p}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" /><path d="M14 2v6h6M9 13h6M9 17h4" /></Svg>
);
export const BuildingIcon = (p) => (
  <Svg {...p}><rect x="4" y="2" width="16" height="20" rx="2" /><path d="M9 22v-4h6v4M8 6h.01M16 6h.01M8 10h.01M16 10h.01M8 14h.01M16 14h.01" /></Svg>
);
export const CapIcon = (p) => (
  <Svg {...p}><path d="m22 9-10-5L2 9l10 5 10-5Z" /><path d="M6 11.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5" /><path d="M22 9v6" /></Svg>
);
export const LogoIcon = (p) => (
  <Svg {...p}><path d="m12 3 9 4.5-9 4.5-9-4.5L12 3Z" /><path d="M6 10v5c0 1.8 2.7 3.2 6 3.2s6-1.4 6-3.2v-5" /><path d="M21 7.5V14" /></Svg>
);
