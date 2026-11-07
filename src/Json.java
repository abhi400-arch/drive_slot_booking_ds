import java.lang.reflect.Array;
import java.util.*;

/** Small JSON reader/writer (no external libraries needed). */
final class Json {
    private Json() {}

    // ---------- writing ----------
    static String write(Object v) { StringBuilder sb = new StringBuilder(); write(v, sb); return sb.toString(); }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb) {
        if (v == null) sb.append("null");
        else if (v instanceof Boolean) sb.append(v);
        else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (v instanceof Double || v instanceof Float) {
                if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
                else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
                else sb.append(d);
            } else sb.append(v);
        }
        else if (v instanceof CharSequence) quote(v.toString(), sb);
        else if (v instanceof Applicant) write(((Applicant) v).toMap(), sb);
        else if (v instanceof Map) {
            sb.append('{'); boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) sb.append(','); first = false;
                quote(e.getKey(), sb); sb.append(':'); write(e.getValue(), sb);
            }
            sb.append('}');
        }
        else if (v instanceof Iterable) {
            sb.append('['); boolean first = true;
            for (Object o : (Iterable<?>) v) { if (!first) sb.append(','); first = false; write(o, sb); }
            sb.append(']');
        }
        else if (v.getClass().isArray()) {
            sb.append('[');
            for (int i = 0; i < Array.getLength(v); i++) { if (i > 0) sb.append(','); write(Array.get(v, i), sb); }
            sb.append(']');
        }
        else quote(v.toString(), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- reading ----------
    /** Parse a JSON object into a Map. Empty / blank text gives an empty map. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) return new LinkedHashMap<>();
        Parser p = new Parser(text);
        Object o = p.value(); p.ws();
        if (p.i != text.length()) throw new IllegalArgumentException("Invalid JSON.");
        if (!(o instanceof Map)) throw new IllegalArgumentException("JSON object expected.");
        return (Map<String, Object>) o;
    }

    /** Value of a key as trimmed text ("" when missing). */
    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return "";
        if (v instanceof Double && (Double) v == Math.rint((Double) v)) return String.valueOf(((Double) v).longValue());
        return v.toString().trim();
    }

    private static class Parser {
        final String s; int i;
        Parser(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        IllegalArgumentException bad() { return new IllegalArgumentException("Invalid JSON."); }

        Object value() {
            ws();
            if (i >= s.length()) throw bad();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i))  { i += 4; return null; }
            int j = i;
            while (j < s.length() && "+-0123456789.eE".indexOf(s.charAt(j)) >= 0) j++;
            if (j == i) throw bad();
            String num = s.substring(i, j); i = j;
            try { return num.matches("-?\\d+") ? (Object) Long.valueOf(num) : (Object) Double.valueOf(num); }
            catch (NumberFormatException e) { throw bad(); }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); if (i >= s.length() || s.charAt(i) != '"') throw bad();
                String k = string(); ws();
                if (i >= s.length() || s.charAt(i) != ':') throw bad();
                i++; m.put(k, value()); ws();
                if (i >= s.length()) throw bad();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == '}') { i++; return m; }
                throw bad();
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value()); ws();
                if (i >= s.length()) throw bad();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == ']') { i++; return l; }
                throw bad();
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder(); i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw bad();
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break; case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': if (i + 4 > s.length()) throw bad();
                                  try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException x) { throw bad(); }
                                  i += 4; break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            throw bad();
        }
    }
}
