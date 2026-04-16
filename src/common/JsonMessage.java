package common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidad JSON minima para mensajes planos (objeto de primer nivel).
 */
public final class JsonMessage {
    private JsonMessage() {
    }

    public static String stringify(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escape(entry.getKey())).append('"').append(':');
            out.append('"').append(escape(entry.getValue())).append('"');
        }
        out.append('}');
        return out.toString();
    }

    public static Map<String, String> parseObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }

        String src = json.trim();
        if (!src.startsWith("{") || !src.endsWith("}")) {
            return result;
        }

        int i = 1;
        int end = src.length() - 1;
        while (i < end) {
            i = skipWhitespace(src, i, end);
            if (i >= end) {
                break;
            }
            if (src.charAt(i) == ',') {
                i++;
                continue;
            }
            if (src.charAt(i) != '"') {
                return new LinkedHashMap<>();
            }

            ParseToken key = readQuoted(src, i);
            if (!key.ok) {
                return new LinkedHashMap<>();
            }
            i = skipWhitespace(src, key.nextIndex, end);
            if (i >= end || src.charAt(i) != ':') {
                return new LinkedHashMap<>();
            }
            i++;
            i = skipWhitespace(src, i, end);
            if (i >= end) {
                return new LinkedHashMap<>();
            }

            ParseToken value;
            if (src.charAt(i) == '"') {
                value = readQuoted(src, i);
            } else {
                value = readRaw(src, i, end);
            }
            if (!value.ok) {
                return new LinkedHashMap<>();
            }

            result.put(key.value, value.value);
            i = value.nextIndex;
        }

        return result;
    }

    public static Map<String, String> mapOf(String... kv) {
        Map<String, String> out = new LinkedHashMap<>();
        if (kv == null) {
            return out;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put(kv[i], kv[i + 1]);
        }
        return out;
    }

    private static int skipWhitespace(String src, int i, int endExclusive) {
        while (i < endExclusive && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        return i;
    }

    private static ParseToken readQuoted(String src, int startQuote) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"', '\\', '/' -> sb.append(ch);
                    default -> sb.append(ch);
                }
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return ParseToken.ok(sb.toString(), i + 1);
            }
            sb.append(ch);
        }
        return ParseToken.fail();
    }

    private static ParseToken readRaw(String src, int start, int endExclusive) {
        int i = start;
        while (i < endExclusive && src.charAt(i) != ',') {
            i++;
        }
        String raw = src.substring(start, i).trim();
        if (raw.isEmpty()) {
            return ParseToken.fail();
        }
        if ("null".equalsIgnoreCase(raw)) {
            return ParseToken.ok("", i);
        }
        return ParseToken.ok(raw, i);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ParseToken(boolean ok, String value, int nextIndex) {
        static ParseToken ok(String value, int nextIndex) {
            return new ParseToken(true, value, nextIndex);
        }

        static ParseToken fail() {
            return new ParseToken(false, "", -1);
        }
    }
}

