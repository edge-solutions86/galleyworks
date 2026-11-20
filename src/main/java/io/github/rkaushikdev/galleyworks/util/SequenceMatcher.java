package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Longest-matching-block sequence comparison over two lists, with no junk heuristic.
 *
 * <p>The matching blocks are found by repeatedly taking the longest contiguous match and recursing
 * on what lies either side of it, then merging blocks that turn out to be adjacent. That is the
 * same answer, block for block, that the word-change counts of an instrument diff have always been
 * computed from; a minimal edit script would count a moved clause differently.
 *
 * @param <T> the element type, compared with {@code equals}
 */
public final class SequenceMatcher<T> {

    /**
     * One run of equal elements: {@code a[left, left + size)} equals {@code b[right, right + size)}.
     *
     * @param left start in the first list
     * @param right start in the second list
     * @param size number of equal elements
     */
    public record Block(int left, int right, int size) {}

    private final List<T> a;
    private final List<T> b;
    private final Map<T, List<Integer>> positions = new HashMap<>();
    private List<Block> blocks;

    /** A matcher comparing {@code a} with {@code b}. */
    public SequenceMatcher(List<T> a, List<T> b) {
        this.a = List.copyOf(a);
        this.b = List.copyOf(b);
        for (int j = 0; j < this.b.size(); j++) {
            positions
                    .computeIfAbsent(this.b.get(j), ignored -> new ArrayList<>())
                    .add(j);
        }
    }

    /** The longest matching block inside {@code a[alo, ahi)} and {@code b[blo, bhi)}. */
    public Block findLongestMatch(int alo, int ahi, int blo, int bhi) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        Map<Integer, Integer> lengths = new HashMap<>();
        for (int i = alo; i < ahi; i++) {
            Map<Integer, Integer> next = new HashMap<>();
            for (int j : positions.getOrDefault(a.get(i), List.of())) {
                if (j < blo) {
                    continue;
                }
                if (j >= bhi) {
                    break;
                }
                int k = lengths.getOrDefault(j - 1, 0) + 1;
                next.put(j, k);
                if (k > bestSize) {
                    bestI = i - k + 1;
                    bestJ = j - k + 1;
                    bestSize = k;
                }
            }
            lengths = next;
        }
        while (bestI > alo && bestJ > blo && a.get(bestI - 1).equals(b.get(bestJ - 1))) {
            bestI--;
            bestJ--;
            bestSize++;
        }
        while (bestI + bestSize < ahi
                && bestJ + bestSize < bhi
                && a.get(bestI + bestSize).equals(b.get(bestJ + bestSize))) {
            bestSize++;
        }
        return new Block(bestI, bestJ, bestSize);
    }

    /** Every matching block in order, adjacent ones merged, ending with a zero-size sentinel. */
    public List<Block> matchingBlocks() {
        if (blocks != null) {
            return blocks;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        queue.push(new int[] {0, a.size(), 0, b.size()});
        List<Block> found = new ArrayList<>();
        while (!queue.isEmpty()) {
            int[] range = queue.pop();
            Block block = findLongestMatch(range[0], range[1], range[2], range[3]);
            if (block.size() > 0) {
                found.add(block);
                if (range[0] < block.left() && range[2] < block.right()) {
                    queue.push(new int[] {range[0], block.left(), range[2], block.right()});
                }
                if (block.left() + block.size() < range[1] && block.right() + block.size() < range[3]) {
                    queue.push(
                            new int[] {block.left() + block.size(), range[1], block.right() + block.size(), range[3]});
                }
            }
        }
        found.sort(Comparator.comparingInt(Block::left)
                .thenComparingInt(Block::right)
                .thenComparingInt(Block::size));
        List<Block> merged = new ArrayList<>();
        int i1 = 0;
        int j1 = 0;
        int k1 = 0;
        for (Block block : found) {
            if (i1 + k1 == block.left() && j1 + k1 == block.right()) {
                k1 += block.size();
            } else {
                if (k1 > 0) {
                    merged.add(new Block(i1, j1, k1));
                }
                i1 = block.left();
                j1 = block.right();
                k1 = block.size();
            }
        }
        if (k1 > 0) {
            merged.add(new Block(i1, j1, k1));
        }
        merged.add(new Block(a.size(), b.size(), 0));
        blocks = List.copyOf(merged);
        return blocks;
    }

    /** Elements in matching blocks. */
    public int matched() {
        return matchingBlocks().stream().mapToInt(Block::size).sum();
    }

    /** Twice the matched elements over the total, or one for two empty lists. */
    public double ratio() {
        int length = a.size() + b.size();
        return length == 0 ? 1.0 : 2.0 * matched() / length;
    }
}
