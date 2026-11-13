package com.example.student_management.service.timetable;

import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the free-text {@code course_sections.schedule} value used by the
 * existing admin screen, for example:
 *
 * <pre>Thứ 2, 07:30 - 09:30, Phòng A2-301</pre>
 *
 * Multiple sessions can be separated by ";", "|" or a newline. The parser
 * never invents a session: segments that do not contain a weekday and a
 * valid time range are ignored.
 */
public final class ScheduleParser {
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*[\\-–—]\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern WEEKDAY_NUMBER = Pattern.compile("thu\\s*([2-7])");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_ROOM = "Phòng chưa cập nhật";

    private ScheduleParser() {
    }

    public record Slot(int dayOfWeek, LocalTime startTime, LocalTime endTime, String room) {
    }

    public static List<Slot> parse(String rawSchedule) {
        List<Slot> slots = new ArrayList<>();
        if (rawSchedule == null || rawSchedule.isBlank()) {
            return slots;
        }

        for (String segment : rawSchedule.split("[;|\\r\\n]+")) {
            Slot slot = parseSegment(segment);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static Slot parseSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }

        String normalized = stripAccents(segment).toLowerCase(Locale.ROOT).trim();
        Integer dayOfWeek = parseDayOfWeek(normalized);
        if (dayOfWeek == null) {
            return null;
        }

        Matcher timeMatcher = TIME_RANGE.matcher(segment);
        if (!timeMatcher.find()) {
            return null;
        }

        LocalTime start = toTime(timeMatcher.group(1), timeMatcher.group(2));
        LocalTime end = toTime(timeMatcher.group(3), timeMatcher.group(4));
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }

        String room = segment.substring(
                Math.min(timeMatcher.end(), segment.length())).trim();
        room = room.replaceAll("^[,;\\-–—\\s]+", "").trim();
        if (room.isEmpty()) {
            room = DEFAULT_ROOM;
        }
        return new Slot(dayOfWeek, start, end, room);
    }

    private static Integer parseDayOfWeek(String normalized) {
        if (normalized.contains("chu nhat") || normalized.contains("chunhat")
                || normalized.matches(".*\\bcn\\b.*")) {
            return 7;
        }

        Matcher numeric = WEEKDAY_NUMBER.matcher(normalized);
        if (numeric.find()) {
            int vietnameseDay = Integer.parseInt(numeric.group(1));
            return vietnameseDay - 1;
        }

        if (normalized.contains("thu hai")) return 1;
        if (normalized.contains("thu ba")) return 2;
        if (normalized.contains("thu tu")) return 3;
        if (normalized.contains("thu nam")) return 4;
        if (normalized.contains("thu sau")) return 5;
        if (normalized.contains("thu bay")) return 6;

        if (normalized.matches(".*\\bt2\\b.*")) return 1;
        if (normalized.matches(".*\\bt3\\b.*")) return 2;
        if (normalized.matches(".*\\bt4\\b.*")) return 3;
        if (normalized.matches(".*\\bt5\\b.*")) return 4;
        if (normalized.matches(".*\\bt6\\b.*")) return 5;
        if (normalized.matches(".*\\bt7\\b.*")) return 6;

        if (normalized.contains("monday") || normalized.contains("mon")) return 1;
        if (normalized.contains("tuesday") || normalized.contains("tue")) return 2;
        if (normalized.contains("wednesday") || normalized.contains("wed")) return 3;
        if (normalized.contains("thursday") || normalized.contains("thu")) return 4;
        if (normalized.contains("friday") || normalized.contains("fri")) return 5;
        if (normalized.contains("saturday") || normalized.contains("sat")) return 6;
        if (normalized.contains("sunday") || normalized.contains("sun")) return 7;

        return null;
    }

    private static LocalTime toTime(String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    public static String format(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    private static String stripAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replace('\u00A0', ' ');
    }
}
