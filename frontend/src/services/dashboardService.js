import { aiChatApi, courseSectionApi, enrollmentApi, gradebookApi, lecturerApi, reportApi, semesterApi, studentApi } from '../api';

const numberOrZero = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
};

const average = (values) => {
  const valid = values
    .filter((v) => v !== null && v !== undefined && v !== '' && !Number.isNaN(Number(v)))
    .map(Number);
  if (!valid.length) return null;
  const total = valid.reduce((sum, v) => sum + v, 0);
  return (total / valid.length).toFixed(2);
};

const getCurrentSemester = (semesters = [], sections = []) => {
  const sorted = [...semesters].sort(
    (left, right) => new Date(right.startDate || 0) - new Date(left.startDate || 0),
  );
  const inProgress = sorted.find((semester) => semester.status === 'IN_PROGRESS')
    || sorted.find((semester) => semester.status === 'REGISTRATION_OPEN');

  if (inProgress) return inProgress;
  if (sections.length && sections[0]?.semester) return sections[0].semester;
  return sorted[0] || null;
};

const buildAcademicSummary = (students = [], sections = [], allGradeEntries = [], semesters = []) => {
  const studentAcademicMap = new Map();

  students.forEach((student) => {
    studentAcademicMap.set(student.id, {
      student,
      gpaValues: [],
      completedCredits: 0,
      passedCredits: 0,
      courses: 0,
      department: student.department || 'Chưa xác định',
    });
  });

  allGradeEntries.forEach((entry) => {
    const studentId = entry.student?.id || entry.studentId;
    if (!studentId) return;
    const studentSummary = studentAcademicMap.get(studentId) || {
      student: entry.student || { id: studentId },
      gpaValues: [],
      completedCredits: 0,
      passedCredits: 0,
      courses: 0,
      department: entry.student?.department || 'Chưa xác định',
    };

    const finalScore = Number(entry.grade?.finalScore ?? entry.finalScore ?? null);
    if (Number.isFinite(finalScore)) {
      studentSummary.gpaValues.push(finalScore);
    }

    const credits = Number(
      entry.courseSection?.course?.credits ?? entry.course?.credits ?? entry.credits ?? 0,
    );
    if (Number.isFinite(credits) && credits > 0) {
      studentSummary.completedCredits += credits;
      studentSummary.courses += 1;
      const isPassed = typeof entry.passed === 'boolean' ? entry.passed : (Number.isFinite(finalScore) && finalScore >= 4);
      if (isPassed) {
        studentSummary.passedCredits += credits;
      }
    }

    studentAcademicMap.set(studentId, studentSummary);
  });

  const summaries = Array.from(studentAcademicMap.values()).map((item) => {
    const gpa = item.gpaValues.length
      ? item.gpaValues.reduce((sum, value) => sum + value, 0) / item.gpaValues.length
      : 0;
    const student = item.student || {};
    return {
      id: student.id,
      studentCode: student.studentCode || student.username || '—',
      fullName: student.fullName || student.user?.fullName || '—',
      department: item.department || student.department || 'Chưa xác định',
      status: student.status || 'ACTIVE',
      gpa: Number(gpa.toFixed(2)),
      completedCredits: item.completedCredits,
      passedCredits: item.passedCredits,
      courseCount: item.courses,
      riskLevel: gpa > 0 && gpa < 2.5 ? 'HIGH' : gpa < 3.5 ? 'MEDIUM' : 'LOW',
    };
  }).filter((item) => item.id != null);

  const lowGpaStudents = summaries
    .filter((student) => student.status === 'ACTIVE' && student.gpa > 0 && student.gpa < 2.5)
    .sort((left, right) => left.gpa - right.gpa)
    .slice(0, 12);

  const lowCreditStudents = summaries
    .filter((student) => student.status === 'ACTIVE' && student.completedCredits > 0 && student.completedCredits < 12)
    .sort((left, right) => left.completedCredits - right.completedCredits)
    .slice(0, 12);

  const currentSemester = getCurrentSemester(semesters, sections);
  const activeSemesterId = currentSemester?.id || null;
  const registeredStudentIds = new Set();

  if (activeSemesterId) {
    allGradeEntries.forEach((entry) => {
      const semesterId = entry.courseSection?.semester?.id
        ?? entry.courseSection?.semesterId
        ?? entry.semesterId;
      const studentId = entry.student?.id || entry.studentId;
      if (semesterId === activeSemesterId && studentId && (entry.status === 'ENROLLED' || !entry.status)) {
        registeredStudentIds.add(Number(studentId));
      }
    });
  }

  const notRegisteredStudents = students
    .filter((student) => student.status === 'ACTIVE' && activeSemesterId && !registeredStudentIds.has(Number(student.id)))
    .map((student) => ({
      id: student.id,
      studentCode: student.studentCode,
      fullName: student.fullName || student.user?.fullName || '—',
      department: student.department || 'Chưa xác định',
      activeSemester: currentSemester?.semesterName || 'Học kỳ hiện tại',
    }));

  const departmentGroups = new Map();
  summaries.forEach((student) => {
    if (!student.department) return;
    const key = student.department;
    const bucket = departmentGroups.get(key) || { label: key, pass: 0, fail: 0, total: 0 };
    bucket.total += 1;
    if (student.gpa >= 4) bucket.pass += 1;
    else if (student.gpa > 0) bucket.fail += 1;
    else bucket.total -= 1;
    departmentGroups.set(key, bucket);
  });

  const passRateByDepartment = Array.from(departmentGroups.values())
    .filter((b) => b.total > 0)
    .map((bucket) => ({
      name: bucket.label,
      passRate: Math.round((bucket.pass / bucket.total) * 100),
      failRate: Math.round((bucket.fail / bucket.total) * 100),
      total: bucket.total,
    }));

  return {
    currentSemester,
    lowGpaStudents,
    lowCreditStudents,
    notRegisteredStudents,
    passRateByDepartment,
    academicSummary: summaries,
    gpaThreshold: 2.5,
    lowCreditThreshold: 12,
  };
};

const loadAdmin = async () => {
  const [reportRes, sectionsRes, studentsRes, lecturersRes, semestersRes] = await Promise.all([
    reportApi.getDashboard(),
    courseSectionApi.getAll(),
    studentApi.getAll().catch(() => ({ data: [] })),
    lecturerApi.getAll().catch(() => ({ data: [] })),
    semesterApi.getAll().catch(() => ({ data: [] })),
  ]);

  const report = reportRes.data || {};
  const sections = sectionsRes.data || [];
  const students = studentsRes.data || [];
  const lecturers = lecturersRes.data || [];
  const semesters = semestersRes.data || [];

  const activeAccounts = students.filter((s) => s.status === 'ACTIVE').length
    + lecturers.filter((l) => l.status === 'ACTIVE').length;
  const inactiveAccounts = students.filter((s) => s.status === 'INACTIVE').length
    + lecturers.filter((l) => l.status === 'INACTIVE').length;

  const statuses = ['DRAFT', 'OPEN', 'IN_PROGRESS', 'CLOSED', 'COMPLETED', 'CANCELLED'];
  const total = sections.length || 1;
  const statusBreakdown = statuses.map((status) => {
    const count = sections.filter((s) => s.status === status).length;
    return { status, count, percent: Math.round((count / total) * 100) };
  });

  const sectionById = new Map(sections.map((s) => [s.id, s]));
  const gradeResponses = await Promise.allSettled(
    sections.map((section) => gradebookApi.getSectionDetail(section.id)),
  );

  const allGradeEntries = [];
  gradeResponses.forEach((result, index) => {
    if (result.status !== 'fulfilled') return;
    const detail = result.value.data || {};
    const section = sectionById.get(detail.sectionId) || sections[index] || {};
    const credits = Number(section?.course?.credits ?? 0);
    const semesterId = section?.semester?.id ?? null;
    (detail.enrollments || []).forEach((entry) => {
      allGradeEntries.push({
        studentId: entry.studentId,
        finalScore: entry.finalScore,
        passed: entry.passed,
        credits,
        semesterId,
        status: 'ENROLLED',
      });
    });
  });

  const academicAlerts = buildAcademicSummary(students, sections, allGradeEntries, semesters);

  return {
    totalStudents: report.totalStudents ?? students.length,
    totalLecturers: report.totalLecturers ?? lecturers.length,
    totalCourses: report.totalCourses ?? 0,
    totalSections: report.totalCourseSections ?? sections.length,
    activeAccounts,
    inactiveAccounts,
    accountOverview: [
      { name: 'Sinh viên', value: report.totalStudents ?? students.length, color: '#2563eb' },
      { name: 'Giảng viên', value: report.totalLecturers ?? lecturers.length, color: '#10b981' },
      { name: 'Đang hoạt động', value: activeAccounts, color: '#22c55e' },
      { name: 'Ngừng hoạt động', value: inactiveAccounts, color: '#f59e0b' },
    ],
    recentSections: [...sections].slice(-6).reverse(),
    statusBreakdown,
    academicAlerts,
    semesters,
    students,
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
    sections.map((s) => gradebookApi.getSectionDetail(s.id)),
  );

  let totalStudents = 0;
  gradeResponses.forEach((result) => {
    if (result.status !== 'fulfilled') return;
    const enrollments = result.value.data?.enrollments || [];
    totalStudents += enrollments.length;
  });

  return {
    sections,
    sectionCount: sections.length,
    openCount: sections.filter((s) => s.status === 'OPEN').length,
    totalCapacity: sections.reduce((sum, s) => sum + numberOrZero(s.capacity), 0),
    enrolledCount: totalStudents,
    totalStudents,
    activeStudents: totalStudents,
    inactiveStudents: 0,
    overview: [
      { name: 'Sinh viên phụ trách', value: totalStudents, color: '#2563eb' },
      { name: 'Đang học', value: totalStudents, color: '#10b981' },
      { name: 'Ngừng học', value: 0, color: '#f59e0b' },
    ],
  };
};

const publishedAtTime = (value) => {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
};

const isPassedGrade = (entry) => {
  if (typeof entry.passed === 'boolean') return entry.passed;
  const attendance = Number(entry.attendanceScore);
  if (Number.isFinite(attendance) && attendance < 4) return false;
  return entry.finalScore >= 4;
};

const loadStudent = async () => {
  const [enrollmentsRes, gradesRes, sectionsRes, overviewRes] = await Promise.all([
    enrollmentApi.getMyEnrollments(),
    studentApi.getMyGrades(),
    courseSectionApi.getAll(),
    aiChatApi.getOverview(),
  ]);

  const enrollments = Array.isArray(enrollmentsRes.data) ? enrollmentsRes.data : [];
  const grades = Array.isArray(gradesRes.data) ? gradesRes.data : [];
  const sections = Array.isArray(sectionsRes.data) ? sectionsRes.data : [];
  const overview = overviewRes.data?.data ?? overviewRes.data ?? {};

  const active = enrollments.filter((e) => e.status === 'ENROLLED');
  const activeCredits = active.reduce(
    (sum, e) => sum + numberOrZero(e.courseSection?.course?.credits),
    0,
  );

  const graded = grades
    .map((g) => {
      const score = Number(g.finalScore);
      if (g.finalScore === null || g.finalScore === undefined || g.finalScore === '' || !Number.isFinite(score)) {
        return null;
      }
      const attendanceComponent = (g.componentScores || []).find(
        (c) => c.componentName && c.componentName.toLowerCase().includes('chuyên cần'),
      );
      return {
        id: g.enrollmentId ?? g.gradebookId,
        enrollmentId: g.enrollmentId,
        courseName: g.courseName || 'Chưa rõ tên học phần',
        courseCode: g.courseCode,
        sectionCode: g.sectionCode,
        credits: numberOrZero(g.credits),
        semesterName: g.semesterName || 'Chưa xác định',
        finalScore: score,
        classification: g.classification,
        passed: g.passed,
        attendanceScore: attendanceComponent?.score ?? null,
        publishedAt: g.publishedAt,
      };
    })
    .filter(Boolean)
    .sort((a, b) => (
      publishedAtTime(b.publishedAt) - publishedAtTime(a.publishedAt)
      || Number(b.id ?? 0) - Number(a.id ?? 0)
    ));

  const validScores = graded.map((e) => e.finalScore);
  const passed = graded.filter(isPassedGrade);
  const failed = graded.filter((entry) => !isPassedGrade(entry));
  const passedCredits = passed.reduce((sum, e) => sum + e.credits, 0);
  const completedCredits = overview.accumulatedCredits ?? passedCredits;
  const currentGpa = overview.gpa10 ?? average(validScores);
  const gpa4 = overview.gpa4 ?? null;

  const semesterMap = new Map();
  graded.forEach((entry) => {
    const semesterName = entry.semesterName || 'Chưa xác định';
    const weight = entry.credits > 0 ? entry.credits : 1;
    const existing = semesterMap.get(semesterName) || { total: 0, weight: 0, latestAt: 0 };
    existing.total += entry.finalScore * weight;
    existing.weight += weight;
    existing.latestAt = Math.max(existing.latestAt, publishedAtTime(entry.publishedAt));
    semesterMap.set(semesterName, existing);
  });

  const gpaTrend = Array.from(semesterMap.entries())
    .sort((a, b) => a[1].latestAt - b[1].latestAt)
    .map(([semester, values]) => ({
      semester,
      gpa: Number((values.total / values.weight).toFixed(2)),
    }));

  return {
    enrollments: active,
    graded,
    enrolledCount: active.length,
    totalCredits: activeCredits,
    activeCredits,
    completedCredits: numberOrZero(completedCredits),
    currentGpa: currentGpa === null || currentGpa === undefined ? null : Number(currentGpa).toFixed(2),
    gpa4: gpa4 === null || gpa4 === undefined ? null : Number(gpa4).toFixed(2),
    passedCourseCount: overview.passedCount ?? passed.length,
    failedCourseCount: overview.failedCount ?? failed.length,
    totalCompletedCourses: graded.length,
    passRate: graded.length ? Math.round((passed.length / graded.length) * 100) : 0,
    averageScore: average(validScores),
    openSections: sections.filter((s) => s.status === 'OPEN').length,
    gpaTrend,
    requiredCredits: null,
  };
};

export const cohortService = {
  load: (role) => {
    if (role === 'ADMIN') return loadAdmin();
    if (role === 'LECTURER') return loadLecturer();
    return loadStudent();
  },
};
