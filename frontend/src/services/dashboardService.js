import { courseSectionApi, enrollmentApi, gradeApi, reportApi } from '../api';

const numberOrZero = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
};

const average = (values) => {
  const valid = values.filter((v) => v !== null && v !== undefined && v !== '' && !Number.isNaN(Number(v))).map(Number);
  if (!valid.length) return null;
  const total = valid.reduce((sum, v) => sum + v, 0);
  return (total / valid.length).toFixed(2);
};

const loadAdmin = async () => {
  const [reportRes, sectionsRes] = await Promise.all([
    reportApi.getDashboard(),
    courseSectionApi.getAll(),
  ]);
  const report = reportRes.data || {};
  const sections = sectionsRes.data || [];
  const statuses = ['DRAFT', 'OPEN', 'IN_PROGRESS', 'CLOSED', 'COMPLETED', 'CANCELLED'];
  const total = sections.length || 1;
  const statusBreakdown = statuses.map((status) => {
    const count = sections.filter((s) => s.status === status).length;
    return { status, count, percent: Math.round((count / total) * 100) };
  });

  return {
    totalStudents: report.totalStudents ?? 0,
    totalLecturers: report.totalLecturers ?? 0,
    totalCourses: report.totalCourses ?? 0,
    totalSections: report.totalCourseSections ?? sections.length,
    recentSections: [...sections].slice(-6).reverse(),
    statusBreakdown,
  };
};

const loadLecturer = async () => {
  let sectionsRes;
  try {
    sectionsRes = await courseSectionApi.getMine();
  } catch {
    sectionsRes = await courseSectionApi.getAll();
  }
  const sections = sectionsRes.data || [];

  const gradeResponses = await Promise.allSettled(
    sections.map((s) => gradeApi.getGradesBySection(s.id)),
  );

  const enrolledCount = gradeResponses.reduce((sum, result) => {
    if (result.status !== 'fulfilled') return sum;
    const list = result.value.data || [];
    return sum + list.filter((e) => e.status === 'ENROLLED').length;
  }, 0);

  return {
    sections,
    sectionCount: sections.length,
    openCount: sections.filter((s) => s.status === 'OPEN').length,
    totalCapacity: sections.reduce((sum, s) => sum + numberOrZero(s.capacity), 0),
    enrolledCount,
  };
};

const loadStudent = async () => {
  const [enrollmentsRes, gradesRes, sectionsRes] = await Promise.all([
    enrollmentApi.getMyEnrollments(),
    gradeApi.getMyGrades().catch(() => ({ data: [] })),
    courseSectionApi.getAll(),
  ]);

  const enrollments = enrollmentsRes.data || [];
  const grades = gradesRes.data || [];
  const sections = sectionsRes.data || [];

  const merged = enrollments.map((e) => {
    const match = grades.find((g) => g.id === e.id);
    return { ...e, grade: match?.grade ?? e.grade };
  });

  const active = merged.filter((e) => e.status === 'ENROLLED');
  const totalCredits = active.reduce((sum, e) => sum + numberOrZero(e.courseSection?.course?.credits), 0);
  const graded = active.filter((e) => e.grade?.finalScore !== null && e.grade?.finalScore !== undefined);

  return {
    enrollments: active,
    graded,
    enrolledCount: active.length,
    totalCredits,
    averageScore: average(graded.map((e) => e.grade.finalScore)),
    openSections: sections.filter((s) => s.status === 'OPEN').length,
  };
};

export const cohortService = {
  load: (role) => {
    if (role === 'ADMIN') return loadAdmin();
    if (role === 'LECTURER') return loadLecturer();
    return loadStudent();
  },
};
