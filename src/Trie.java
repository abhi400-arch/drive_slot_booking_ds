import java.util.*;

/** Prefix tree (Trie) for fast applicant-name autocomplete and prefix search. */
class Trie {
    static class Node {
        Map<Character, Node> next = new TreeMap<>();
        List<Applicant> matches = new ArrayList<>();
    }
    private final Node root = new Node();

    void insert(String word, Applicant applicant) {
        Node cur = root;
        for (char ch : word.toLowerCase(Locale.ROOT).toCharArray()) {
            cur = cur.next.computeIfAbsent(ch, k -> new Node());
            if (cur.matches.size() < 50) cur.matches.add(applicant);
        }
    }

    List<Applicant> prefix(String prefix) {
        Node cur = root;
        for (char ch : prefix.toLowerCase(Locale.ROOT).toCharArray()) {
            cur = cur.next.get(ch);
            if (cur == null) return List.of();
        }
        return new ArrayList<>(cur.matches);
    }

    static Trie from(List<Applicant> records) {
        Trie t = new Trie();
        for (Applicant a : records) t.insert(a.name, a);
        return t;
    }
}
