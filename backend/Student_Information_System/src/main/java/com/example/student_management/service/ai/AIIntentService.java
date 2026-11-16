package com.example.student_management.service.ai;

import java.util.Locale;
import java.util.regex.Pattern;

public class AIIntentService {

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
        OUT_OF_SCOPE
    }

    public Intent detectIntent(String question, boolean hasConversationContext) {
        if (question == null || question.trim().isEmpty()) return Intent.GENERAL_ACADEMIC_QUESTION;
        String q = normalize(question);

        if (isOutOfScope(q)) return Intent.OUT_OF_SCOPE;
        if (hasConversationContext && isFollowUp(q)) return Intent.FOLLOW_UP_QUESTION;

        if (containsAny(q, "ho so", "thong tin ca nhan", "ma sinh vien", "ma sv", "lop hanh chinh", "nganh", "khoa", "email", "trang thai hoc tap")) {
            return Intent.STUDENT_PROFILE;
        }
        if (containsAny(q, "gpa", "diem trung binh", "dtb")) {
            return Intent.GPA_QUERY;
        }
        if (containsAny(q, "tin chi", "tich luy", "credit", "bao nhieu tin")) {
            return Intent.CREDIT_QUERY;
        }
        if (containsAny(q, "chua hoan thanh", "no mon", "mon no", "rot", "truot", "hoc lai", "chua dat", "can cai thien mon")) {
            return Intent.INCOMPLETE_COURSE_QUERY;
        }
        if (containsAny(q, "diem mon", "diem so", "bang diem", "ket qua hoc tap", "diem cao nhat", "diem thap nhat", "xep loai")) {
            return Intent.GRADE_QUERY;
        }
        if (containsAny(q, "dang ky", "dang hoc", "lop hoc phan", "thoi khoa bieu", "lich hoc hom nay", "lich hoc tuan", "enrollment", "section")) {
            return Intent.ENROLLMENT_QUERY;
        }
        if (containsAny(q, "hoc ky", "hoc ki", "ky hoc", "semester", "theo ky", "qua cac ky")) {
            return Intent.SEMESTER_QUERY;
        }
        if (containsAny(q, "mon hoc", "hoc phan", "course", "khoa hoc", "mon nao")) {
            return Intent.COURSE_QUERY;
        }
        if (containsAny(q, "tien do", "tong quan", "tinh hinh hoc tap", "ket qua chung", "phan tich hoc tap", "bao nhieu mon")) {
            return Intent.ACADEMIC_PROGRESS;
        }
        return Intent.GENERAL_ACADEMIC_QUESTION;
    }

    private boolean isFollowUp(String q) {
        // short or referential questions
        if (q.length() <= 40 && (containsAny(q, "mon do", "mon nao", "tai sao", "vi sao", "nhu the nao", "cu the hon", "phan tich ky hon", "giai thich them", "con", "tiep", "the thi", "neu vay", "no", "y tren", "ket qua tren", "o tren", "vua roi", "luc nay"))) {
            return true;
        }
        return Pattern.compile("^(mon|ket qua|diem|tien do|cai|no|vay|the).{0,30}\\?$").matcher(q).find();
    }

    private boolean isOutOfScope(String q) {
        return containsAny(q, "thoi tiet", "gia vang", "chinh tri", "tin tuc", "ca nhac", "phim", "hen ho", "viet code giup toi hack", "mat khau", "api key");
    }

    private boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    private String normalize(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replace("đ", "d");
        s = s.replaceAll("\\s+", " ");
        return s;
    }
}
