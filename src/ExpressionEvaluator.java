import java.util.*;

/** Evaluates an infix arithmetic expression with two stacks (operators + values) via postfix conversion. */
class ExpressionEvaluator {
    static class Result { List<String> postfix = new ArrayList<>(); double value; }

    static Result evaluate(String expr) {
        if (expr == null || expr.isBlank()) throw new IllegalArgumentException("Enter an expression, e.g. (8 + 2) * 3 - 4");
        List<String> tokens = tokenize(expr);
        Result r = new Result();

        // 1. infix -> postfix using an operator stack
        Deque<String> ops = new ArrayDeque<>();
        for (String t : tokens) {
            if (isNumber(t)) r.postfix.add(t);
            else if (t.equals("(")) ops.push(t);
            else if (t.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) r.postfix.add(ops.pop());
                if (ops.isEmpty()) throw new IllegalArgumentException("Mismatched parentheses.");
                ops.pop();
            } else {
                while (!ops.isEmpty() && !ops.peek().equals("(") && prec(ops.peek()) >= prec(t)) r.postfix.add(ops.pop());
                ops.push(t);
            }
        }
        while (!ops.isEmpty()) {
            String o = ops.pop();
            if (o.equals("(")) throw new IllegalArgumentException("Mismatched parentheses.");
            r.postfix.add(o);
        }

        // 2. evaluate postfix using a value stack
        Deque<Double> st = new ArrayDeque<>();
        for (String t : r.postfix) {
            if (isNumber(t)) { st.push(Double.parseDouble(t)); continue; }
            if (st.size() < 2) throw new IllegalArgumentException("Invalid expression.");
            double b = st.pop(), a = st.pop();
            switch (t) {
                case "+": st.push(a + b); break;
                case "-": st.push(a - b); break;
                case "*": st.push(a * b); break;
                case "/": if (b == 0) throw new IllegalArgumentException("Division by zero."); st.push(a / b); break;
                default: throw new IllegalArgumentException("Unknown operator " + t);
            }
        }
        if (st.size() != 1) throw new IllegalArgumentException("Invalid expression.");
        r.value = st.pop();
        return r;
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (Character.isDigit(ch) || ch == '.') {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                String num = s.substring(i, j);
                if (num.chars().filter(x -> x == '.').count() > 1 || num.equals(".")) throw new IllegalArgumentException("Bad number '" + num + "'.");
                out.add(num); i = j; continue;
            }
            if ("+-*/()".indexOf(ch) < 0) throw new IllegalArgumentException("Unexpected character '" + ch + "'.");
            // unary minus: "-5" or "(-5)" becomes "0 - 5"
            if (ch == '-' && (out.isEmpty() || out.get(out.size() - 1).equals("(") || isOperator(out.get(out.size() - 1)))) out.add("0");
            out.add(String.valueOf(ch)); i++;
        }
        return out;
    }

    private static boolean isOperator(String t) { return t.length() == 1 && "+-*/".contains(t); }
    private static boolean isNumber(String t) { return Character.isDigit(t.charAt(0)) || t.charAt(0) == '.'; }
    private static int prec(String o) { return (o.equals("+") || o.equals("-")) ? 1 : 2; }
}
