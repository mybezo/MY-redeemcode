package com.mybezo.redeemcode.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and formatting of human readable durations such as {@code 1d12h30m}.
 */
public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([smhdw])");
    private static final Pattern FULL = Pattern.compile("^(\\d+\\s*[smhdw]\\s*)+$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    /**
     * @return duration in millis, 0 for "never"/"0", or -1 when invalid.
     */
    public static long parseDuration(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("never") || s.equals("none") || s.equals("0")) {
            return 0;
        }
        if (!FULL.matcher(s).matches()) {
            return -1;
        }
        long total = 0;
        Matcher m = PART.matcher(s);
        while (m.find()) {
            long value = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 604_800_000L;
                default -> 0L;
            };
            total = Math.addExact(total, Math.multiplyExact(value, unit));
        }
        return total <= 0 ? -1 : total;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public static String formatDate(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }
}
