package nl.inl.blacklab.testutil;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import nl.inl.blacklab.forwardindex.ParallelIntSorter;

public class TestSortPerformance {

    private static int[] toSort;

    private static int[] lookup;

    private static IntComparator comp;

    @FunctionalInterface
    interface Sorter {
        void sort(int[] arr, IntComparator comp);
    }

    public static void main(String[] args) {
        System.out.println("         n   FQS  FPQ  PIS");
        int[] testSizes = { 1000, 10000, 100_000, 150_000, 250_000,
                500_000, 750_000, 1_000_000, 2_500_000, 5_000_000, 7_500_000, 10_000_000 };
        for (int size: testSizes) {
            test(size);
        }
    }

    private static void test(int n) {
        toSort = randomArray(n, n);
        lookup = randomArray(n, n);
        comp = (a, b) -> Integer.compare(lookup[a], lookup[b]);

        Sorter[] sorters = {
            IntArrays::quickSort,
            IntArrays::parallelQuickSort,
            ParallelIntSorter::sort
        };

        long fastest = Long.MAX_VALUE;
        int fastestIndex = -1;
        System.out.print(String.format("%10d ", n));
        for (int i = 0; i < sorters.length; i++) {
            long time = timeSort(sorters[i]);
            if (time < fastest) {
                fastest = time;
                fastestIndex = i;
            }
            System.out.print(String.format("%5d", time));
        }
        // Highlight best result
        System.out.println(String.format("\n          %" + (5 * fastestIndex + 1) + "s    *", ""));
    }

    private static long timeSort(Sorter sorter) {
        int[] a = Arrays.copyOf(toSort, toSort.length);
        long start = System.currentTimeMillis();
        sorter.sort(a, comp);
        long end = System.currentTimeMillis();
        return end - start;
    }

    private static int[] randomArray(int length, int maxValue) {
        int[] array = new int[length];
        for (int i = 0; i < array.length; i++) {
            array[i] = (int)(Math.random() * maxValue);
        }
        return array;
    }
}
