package com.example.student_management.service.ai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class AIIntentService {

    public static final double CONFIDENCE_THRESHOLD = 0.60;

    public enum Intent {
        STUDENT_PROFILE,
        ACADEMIC_PROGRESS,
        GPA_QUERY,
        CREDIT_QUERY,
        COURSE_QUERY,
        ENROLLMENT_QUERY,
        GRADE_QUERY,
        SEMESTER_QUERY,
        INCOMPLETE_COURSE_QUERY,
        GENERAL_ACADEMIC_QUESTION,
        FOLLOW_UP_QUESTION,
        COMBINED_SIS_TUTOR,
        TUTOR_EXPLANATION,
        TUTOR_EXERCISE,
        TUTOR_CODE_REVIEW,
        TUTOR_QUIZ,
        TUTOR_ANSWER_EVALUATION,
        TUTOR_STUDY_MATERIAL,
        TUTOR_FOLLOW_UP,
        OUT_OF_SCOPE,
        COURSE_ADVISOR,
        GPA_WHAT_IF,
        STUDY_PLANNER,
        QUIZ_GENERATION,
        QUIZ_ANSWER_SUBMISSION,
        TIMETABLE_ASSISTANT,
        EXAM_SCHEDULE,
        LEARNING_INSIGHTS,
        COURSE_INFORMATION,
        GENERAL_KNOWLEDGE,
        UNKNOWN
    }

    /** Thông tin tối thiểu của lượt trước, do Conversation Context Manager cung cấp. */
    public static class PriorContext {
        private final Intent intent;
        private final String topic;
        private final Map<String, String> slots;

        public PriorContext(Intent intent, String topic, Map<String, String> slots) {
            this.intent = intent;
            this.topic = topic;
            this.slots = slots == null ? new HashMap<>() : new HashMap<>(slots);
        }

        public Intent getIntent() { return intent; }
        public String getTopic() { return topic; }
        public Map<String, String> getSlots() { return slots; }
    }

    public static class IntentAnalysis {
        private final Intent intent;
        private final AITutorService.TutorPlan tutorPlan;
        private final double confidence;
        private final boolean needsClarification;

        public IntentAnalysis(Intent intent, AITutorService.TutorPlan tutorPlan) {
            this(intent, tutorPlan, intent == Intent.UNKNOWN ? 0.0 : 0.80, intent == Intent.UNKNOWN);
        }

        public IntentAnalysis(Intent intent, AITutorService.TutorPlan tutorPlan,
                              double confidence, boolean needsClarification) {
            this.intent = intent;
            this.tutorPlan = tutorPlan;
            this.confidence = confidence;
            this.needsClarification = needsClarification;
        }

        public Intent getIntent() { return intent; }
        public AITutorService.TutorPlan getTutorPlan() { return tutorPlan; }
        public double getConfidence() { return confidence; }
        public boolean isNeedsClarification() { return needsClarification; }
    }

    private static class Detection {
        final Intent intent;
        final int score;
        final boolean needsClarification;

        Detection(Intent intent, int score, boolean needsClarification) {
            this.intent = intent;
            this.score = score;
            this.needsClarification = needsClarification;
        }
    }

    private static final Pattern QUIZ_COUNT = Pattern.compile(
            "(?:tao|lam|cho\\s+toi|can).{0,30}?(\\d{1,2})\\s*(?:cau\\s*hoi|cau)\\s*(?:trac\\s*nghiem|quiz)?"
                    + "|(\\d{1,2})\\s*cau\\s*(?:trac\\s*nghiem|quiz)");
    private static final Pattern GPA_TARGET = Pattern.compile(
            "(?:gpa|dtb|diem\\s*trung\\s*binh).{0,30}?([0-4](?:[.,]\\d{1,2})?)");
    private static final Pattern CREDIT_COUNT = Pattern.compile(
            "(\\d{1,2})\\s*(?:tc|tin\\s*chi|tich\\s*luy)");
    private static final Pattern ANSWER_LINE = Pattern.compile(
            "\\d{1,2}\\s*[:.)-]?\\s*[abcd]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC_SWITCH = Pattern.compile(
            "nen dang ky|ke hoach hoc|phan tich lich hoc|lich hoc va lich thi|lich thi"
                    + "|phan tich ket qua hoc tap|gpa.*(?:neu|gia dinh|mo phong)|mo phong gpa|trac nghiem");

    private final AITutorService tutorService = new AITutorService();

    public IntentAnalysis analyze(String question, boolean hasConversationContext) {
        return analyze(question, hasConversationContext, null);
    }

    public IntentAnalysis analyze(String question, boolean hasConversationContext, PriorContext prior) {
        Detection detection = detect(question, hasConversationContext, prior);
        double confidence = Math.min(1.0, 0.50 + 0.10 * detection.score);
        boolean clarify = detection.needsClarification || confidence < CONFIDENCE_THRESHOLD;

        AITutorService.TutorPlan plan = null;
        if (isTutorIntent(detection.intent)) {
            String context = prior == null ? null : prior.getTopic();
            plan = tutorService.plan(question, context);
            if (prior != null && context != null && !context.isBlank()
                    && (detection.intent == Intent.TUTOR_FOLLOW_UP || isFollowUpPhrase(question))) {
                plan = new AITutorService.TutorPlan(plan.getTask(), plan.getLevel(), trimTopic(context),
                        plan.getInstructions());
            }
        }
        return new IntentAnalysis(detection.intent, plan, confidence, clarify);
    }

    public Intent detectIntent(String question, boolean hasConversationContext) {
        return detect(question, hasConversationContext, null).intent;
    }

    /** Giữ API cũ cho luồng Phase 3-6; phiên bản Phase 7 dùng detect() có scoring. */
    public Intent detectSisIntent(String question, boolean hasConversationContext) {
        if (question == null || question.trim().isEmpty()) return Intent.UNKNOWN;
        String q = normalize(question);
        if (isOutOfScope(q)) return Intent.OUT_OF_SCOPE;
        return legacySisIntent(q).intent;
    }

    public boolean isTutorIntent(Intent intent) {
        return intent == Intent.COMBINED_SIS_TUTOR
                || intent == Intent.TUTOR_EXPLANATION
                || intent == Intent.TUTOR_EXERCISE
                || intent == Intent.TUTOR_CODE_REVIEW
                || intent == Intent.TUTOR_QUIZ
                || intent == Intent.TUTOR_ANSWER_EVALUATION
                || intent == Intent.TUTOR_STUDY_MATERIAL
                || intent == Intent.TUTOR_FOLLOW_UP
                || intent == Intent.GENERAL_KNOWLEDGE;
    }

    public boolean isStructuredBusinessIntent(Intent intent) {
        return intent == Intent.COURSE_ADVISOR
                || intent == Intent.GPA_WHAT_IF
                || intent == Intent.STUDY_PLANNER
                || intent == Intent.QUIZ_GENERATION
                || intent == Intent.QUIZ_ANSWER_SUBMISSION
                || intent == Intent.TIMETABLE_ASSISTANT
                || intent == Intent.EXAM_SCHEDULE
                || intent == Intent.LEARNING_INSIGHTS;
    }

    public boolean isFollowUpPhrase(String question) {
        String q = normalize(question);
        if (q.length() <= 80 && containsAny(q,
                "vi du cu the", "cho toi mot vi du", "giai thich ky hon", "giai thich them", "giai thich ro hon",
                "tiep tuc", "tiep tuc di", "minh hoa bang code", "code java", "co code", "so sanh voi cach",
                "con truong hop", "truong hop nao khac", "tai sao lai", "tai sao", "vi sao", "no la gi", "cua no",
                "them 15 tin", "con tuan sau", "dap an cua toi", "day la dap an", "day la cau tra loi")) {
            return true;
        }
        if (ANSWER_LINE.matcher(q).find() && q.length() < 160) return true;
        return Pattern.compile("^(mon|ket qua|diem|tien do|cai|no|vay|the).{0,30}\\?$").matcher(q).find();
    }

    public boolean isTopicSwitch(String question) {
        return TOPIC_SWITCH.matcher(normalize(question)).find();
    }

    private Detection detect(String question, boolean hasConversationContext, PriorContext prior) {
        if (question == null || question.trim().isEmpty()) {
            return new Detection(Intent.UNKNOWN, 0, true);
        }
        String q = normalize(question);
        if (isOutOfScope(q)) return new Detection(Intent.OUT_OF_SCOPE, 5, false);

        boolean looksLikeQuizAnswer = ANSWER_LINE.matcher(q).find()
                || (containsAny(q, "dap an cua toi", "day la dap an", "cham diem", "cau tra loi cua toi")
                        && prior != null && prior.getIntent() == Intent.QUIZ_GENERATION);
        if (looksLikeQuizAnswer && (prior != null && prior.getIntent() == Intent.QUIZ_GENERATION
                || containsAny(q, "dap an cua toi", "day la dap an"))) {
            return new Detection(Intent.QUIZ_ANSWER_SUBMISSION, 6, false);
        }

        Map<Intent, Integer> scores = new LinkedHashMap<>();
        addScore(scores, Intent.GPA_WHAT_IF, gpaWhatIfScore(q));
        addScore(scores, Intent.COURSE_ADVISOR, courseAdvisorScore(q));
        addScore(scores, Intent.STUDY_PLANNER, studyPlannerScore(q));
        addScore(scores, Intent.QUIZ_GENERATION, quizScore(q));
        addScore(scores, Intent.TIMETABLE_ASSISTANT, timetableScore(q));
        addScore(scores, Intent.EXAM_SCHEDULE, examScore(q));
        addScore(scores, Intent.LEARNING_INSIGHTS, insightsScore(q));
        addScore(scores, Intent.COURSE_INFORMATION, courseInformationScore(q));

        Intent business = topIntent(scores);
        int businessScore = scores.getOrDefault(business, 0);
        if (businessScore >= 3) {
            boolean ambiguous = scores.values().stream().filter(v -> v >= businessScore - 1).count() > 1;
            return new Detection(business, businessScore, ambiguous);
        }

        boolean tutor = tutorService.isTutorQuestion(question);
        boolean sisMention = tutorService.mentionsSisData(question);
        if (tutor && sisMention && businessScore < 3) {
            return new Detection(Intent.COMBINED_SIS_TUTOR, 5, false);
        }
        if (hasConversationContext && isFollowUpPhrase(q)) {
            return followUpDetection(prior, q);
        }
        if (tutor) {
            AITutorService.TutorTask task = tutorService.plan(question, null).getTask();
            Intent tutorIntent = mapTutorIntent(task);
            if (task == AITutorService.TutorTask.GENERAL) tutorIntent = Intent.GENERAL_KNOWLEDGE;
            return new Detection(tutorIntent, 3, false);
        }

        Detection legacy = legacySisIntent(q);
        if (legacy.score >= 2) return legacy;
        if (hasConversationContext) return new Detection(Intent.FOLLOW_UP_QUESTION, 2, false);
        return new Detection(Intent.UNKNOWN, 1, true);
    }

    private Detection followUpDetection(PriorContext prior, String q) {
        if (prior == null || prior.getIntent() == null) {
            return new Detection(Intent.FOLLOW_UP_QUESTION, 2, true);
        }
        Intent last = prior.getIntent();
        if (last == Intent.QUIZ_GENERATION) {
            return new Detection(looksLikeAnswerText(q) ? Intent.QUIZ_ANSWER_SUBMISSION : Intent.QUIZ_GENERATION, 5, false);
        }
        if (last == Intent.GPA_QUERY && containsAny(q, "them", "tin chi", "neu", "gia dinh", "mo phong")) {
            return new Detection(Intent.GPA_WHAT_IF, 5, false);
        }
        if (isTutorIntent(last) || last == Intent.GENERAL_KNOWLEDGE) {
            return new Detection(Intent.TUTOR_FOLLOW_UP, 5, false);
        }
        if (isStructuredBusinessIntent(last) || last == Intent.GPA_QUERY || last == Intent.GRADE_QUERY
                || last == Intent.ENROLLMENT_QUERY || last == Intent.SEMESTER_QUERY) {
            return new Detection(last, 5, false);
        }
        return new Detection(Intent.FOLLOW_UP_QUESTION, 4, false);
    }

    private boolean looksLikeAnswerText(String q) {
        return ANSWER_LINE.matcher(q).find()
                || containsAny(q, "dap an", "tra loi", "cham diem", "ket qua cua toi", "cau tra loi");
    }

    private Detection legacySisIntent(String q) {
        if (containsAny(q, "ho so", "thong tin ca nhan", "ma sinh vien", "ma sv", "lop hanh chinh", "nganh", "khoa", "email", "trang thai hoc tap")) {
            return new Detection(Intent.STUDENT_PROFILE, 3, false);
        }
        if (containsAny(q, "gpa", "diem trung binh", "dtb")) return new Detection(Intent.GPA_QUERY, 3, false);
        if (containsAny(q, "tin chi", "tich luy", "credit", "bao nhieu tin")) return new Detection(Intent.CREDIT_QUERY, 3, false);
        if (containsAny(q, "chua hoan thanh", "no mon", "mon no", "rot", "truot", "hoc lai", "chua dat", "can cai thien mon")) {
            return new Detection(Intent.INCOMPLETE_COURSE_QUERY, 3, false);
        }
        if (containsAny(q, "diem mon", "diem so", "bang diem", "ket qua hoc tap", "diem cao nhat", "diem thap nhat", "xep loai")) {
            return new Detection(Intent.GRADE_QUERY, 3, false);
        }
        if (containsAny(q, "dang ky", "dang hoc", "lop hoc phan", "thoi khoa bieu", "lich hoc hom nay", "lich hoc tuan", "enrollment", "section")) {
            return new Detection(Intent.ENROLLMENT_QUERY, 3, false);
        }
        if (containsAny(q, "hoc ky", "hoc ki", "ky hoc", "semester", "theo ky", "qua cac ky")) {
            return new Detection(Intent.SEMESTER_QUERY, 2, false);
        }
        if (containsAny(q, "mon hoc", "hoc phan", "course", "khoa hoc", "mon nao")) {
            return new Detection(Intent.COURSE_QUERY, 2, false);
        }
        if (containsAny(q, "tien do", "tong quan", "tinh hinh hoc tap", "ket qua chung", "phan tich hoc tap", "bao nhieu mon")) {
            return new Detection(Intent.ACADEMIC_PROGRESS, 3, false);
        }
        return new Detection(Intent.GENERAL_ACADEMIC_QUESTION, 1, false);
    }

    private int gpaWhatIfScore(String q) {
        boolean gpa = containsAny(q, "gpa", "dtb", "diem trung binh");
        boolean simulation = containsAny(q, "neu", "gia dinh", "mo phong", "what if", "se thay doi", "thay doi ra sao",
                "se tang", "se giam", "them tin chi");
        if (gpa && simulation && (GPA_TARGET.matcher(q).find() || CREDIT_COUNT.matcher(q).find())) return 6;
        if (gpa && simulation) return 5;
        if (containsAny(q, "mo phong", "what-if", "what if")) return 3;
        return 0;
    }

    private int courseAdvisorScore(String q) {
        int score = 0;
        if (containsAny(q, "nen dang ky", "nen hoc mon", "tu van mon", "mon nao nen", "dang ky mon nao", "ke hoach dang ky")) score += 4;
        if (containsAny(q, "dieu kien tien quyet", "so tin chi", "canh bao", "lop mo", "hoc ky toi", "hoc ky tiep")) score += 2;
        if (containsAny(q, "mon hoc", "hoc phan")) score += 1;
        return Math.min(score, 6);
    }

    private int studyPlannerScore(String q) {
        int score = 0;
        if (containsAny(q, "ke hoach hoc tap", "ke hoach on tap", "lap ke hoach", "lich on tap", "plan hoc")) score += 4;
        if (containsAny(q, "ngay", "tuan", "thoi gian hoc")) score += 1;
        if (containsAny(q, "uu tien diem thap", "diem thap", "mon sap thi", "can cai thien")) score += 1;
        return Math.min(score, 6);
    }

    private int quizScore(String q) {
        if (QUIZ_COUNT.matcher(q).find()) return 6;
        if (containsAny(q, "cau hoi trac nghiem", "bai trac nghiem", "lam bai", "cham diem va giai thich")) return 5;
        return 0;
    }

    private int timetableScore(String q) {
        if (containsAny(q, "lich hoc va lich thi", "lich hoc va lich thi", "phan tich lich hoc", "thoi khoa bieu va lich thi")) return 6;
        if (containsAny(q, "thoi khoa bieu", "lich hoc")) return 3;
        return 0;
    }

    private int examScore(String q) {
        if (containsAny(q, "lich thi", "ngay thi", "phong thi", "ky thi")) return 4;
        return 0;
    }

    private int insightsScore(String q) {
        if (containsAny(q, "phan tich ket qua hoc tap", "tong hop ket qua hoc tap", "phan tich tinh hinh hoc tap")) return 6;
        if (containsAny(q, "xuan huong", "muc tieu cai thien", "de xuat cai thien", "nhan xet hoc tap")) return 4;
        if (containsAny(q, "tong quan hoc tap", "ket qua hoc tap")) return 2;
        return 0;
    }

    private int courseInformationScore(String q) {
        return containsAny(q, "thong tin mon hoc", "thong tin hoc phan", "mon hoc la gi", "hoc phan la gi") ? 3 : 0;
    }

    private void addScore(Map<Intent, Integer> scores, Intent intent, int score) {
        if (score > 0) scores.put(intent, score);
    }

    private Intent topIntent(Map<Intent, Integer> scores) {
        Intent best = Intent.UNKNOWN;
        int max = 0;
        for (Map.Entry<Intent, Integer> e : scores.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private Intent mapTutorIntent(AITutorService.TutorTask task) {
        if (task == null) return Intent.TUTOR_EXPLANATION;
        return switch (task) {
            case EXERCISE -> Intent.TUTOR_EXERCISE;
            case CODE_REVIEW -> Intent.TUTOR_CODE_REVIEW;
            case QUIZ -> Intent.TUTOR_QUIZ;
            case ANSWER_EVALUATION -> Intent.TUTOR_ANSWER_EVALUATION;
            case STUDY_MATERIAL -> Intent.TUTOR_STUDY_MATERIAL;
            case FOLLOW_UP -> Intent.TUTOR_FOLLOW_UP;
            default -> Intent.TUTOR_EXPLANATION;
        };
    }

    private boolean isOutOfScope(String q) {
        return containsAny(q, "thoi tiet", "gia vang", "chinh polit", "chinh tri", "tin tuc", "ca nhac", "phim", "hen ho", "viet code giup toi hack", "mat khau", "api key");
    }

    private boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(normalize(k))) return true;
        }
        return false;
    }

    private String trimTopic(String topic) {
        String t = AIInputSafety.sanitizeData(topic);
        return t.length() > 90 ? t.substring(0, 87) + "..." : t;
    }

    private String normalize(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replace("đ", "d");
        return s.replaceAll("\\s+", " ");
    }
}
