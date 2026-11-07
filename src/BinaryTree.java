import java.util.*;

/** General binary tree (built level by level) used to show hierarchical organisation of RTO centres. */
class BinaryTree {
    static class Node {
        String val; Node left, right;
        Node(String v) { val = v; }
    }

    Node root;
    private int size;

    /** Insert in level order so the tree stays complete (parent -> up to two children). */
    void insertLevelOrder(String v) {
        Node n = new Node(v);
        size++;
        if (root == null) { root = n; return; }
        Queue<Node> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            Node c = q.poll();
            if (c.left == null) { c.left = n; return; }
            if (c.right == null) { c.right = n; return; }
            q.add(c.left); q.add(c.right);
        }
    }

    List<String> preorder()  { List<String> o = new ArrayList<>(); pre(root, o);  return o; }
    List<String> inorder()   { List<String> o = new ArrayList<>(); in(root, o);   return o; }
    List<String> postorder() { List<String> o = new ArrayList<>(); post(root, o); return o; }
    private void pre(Node n, List<String> o)  { if (n == null) return; o.add(n.val); pre(n.left, o); pre(n.right, o); }
    private void in(Node n, List<String> o)   { if (n == null) return; in(n.left, o); o.add(n.val); in(n.right, o); }
    private void post(Node n, List<String> o) { if (n == null) return; post(n.left, o); post(n.right, o); o.add(n.val); }

    int height() { return height(root); }
    private int height(Node n) { return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right)); }
    int size() { return size; }

    Map<String, Object> toMap() { return toMap(root); }
    private Map<String, Object> toMap(Node n) {
        if (n == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", n.val); m.put("left", toMap(n.left)); m.put("right", toMap(n.right));
        return m;
    }
}
