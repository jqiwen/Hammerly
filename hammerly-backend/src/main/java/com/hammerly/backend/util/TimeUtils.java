package com.hammerly.backend.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class TimeUtils {
    private static final DateTimeFormatter LEGACY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TimeUtils() {
    }

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DateTimeParseException("Missing date", value == null ? "" : value, 0);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDateTime localDateTime;
            try {
                localDateTime = LocalDateTime.parse(value, LEGACY_TIMESTAMP);
            } catch (DateTimeParseException second) {
                localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
    }

    public static String timeRemaining(String endTime) {
        long diffMinutes;
        try {
            diffMinutes = (parse(endTime).toEpochMilli() - System.currentTimeMillis()) / 60_000;
        } catch (DateTimeParseException exception) {
            return "Ended";
        }
        if (diffMinutes <= 0) return "Ended";
        long days = diffMinutes / (24 * 60);
        long hours = (diffMinutes % (24 * 60)) / 60;
        long minutes = diffMinutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public static int progress(String startTime, String endTime) {
        try {
            long start = parse(startTime).toEpochMilli();
            long end = parse(endTime).toEpochMilli();
            long now = System.currentTimeMillis();
            if (end <= start) return 0;
            if (now <= start) return 0;
            if (now >= end) return 100;
            return (int) Math.round(((double) (now - start) / (end - start)) * 100);
        } catch (DateTimeParseException exception) {
            return 0;
        }
    }

    public static boolean hasEnded(String endTime) {
        try {
            return parse(endTime).toEpochMilli() <= System.currentTimeMillis();
        } catch (DateTimeParseException exception) {
            return true;
        }
    }

    public static String localDate(String value) {
        return DISPLAY_DATE.format(parse(value).atZone(ZoneId.systemDefault()));
    }

    public static String readInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant().toString();
    }
}
