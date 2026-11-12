package jp.hane.gesture.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser (objects, arrays, strings, numbers, booleans, null).
 * Just enough for the gesture app's pose messages. No external dependencies.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            if (i < s.length() && s.charAt(i) == ':') i++;
            ws();
            m.put(key, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            return m;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
        while (true) {
            ws();
            list.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            return list;
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) return 0.0;
        try { return Double.parseDouble(s.substring(start, i)); }
        catch (NumberFormatException e) { return 0.0; }
    }

    // ---- typed helpers ----
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    public static double asDouble(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    public static String asString(Object o) {
        return o instanceof String ? (String) o : "";
    }
}
