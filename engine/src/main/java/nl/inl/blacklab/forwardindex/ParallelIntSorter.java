package nl.inl.blacklab.forwardindex;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;

/**
 * Quickly sort very large primitive integer array in parallel.
 *
 * We need this for determining global term sort order while opening very large integrated indexes.
 *
 * This version was initially generated by ChatGPT, but thoroughly tested, fixed and improved by hand.
 * Surprisingly, it is now faster for very large arrays (10M or so) than FastUtil's
 * IntArrays.parallelQuickSort().
 *
 * These improvements on the initial version were made:
 * - originally used to create executor once, but shut it down after every call
 * - shutdown the executor before the first two tasks had a chance to add other tasks
 * - originally used Comparator, causing bad performance through boxing/unboxing
 * - use FastUtil's quicksort for sorting smaller arrays as it's cleverer than basic quicksort
 * - changed static methods to instance methods for threadsafety
 */
public class ParallelIntSorter {

    private static final int THRESHOLD = 10_000; // Minimum size of array to parallelize sorting

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors(); // Number of threads to use

    private static final Random random = new Random();

    /** Give each searchthread a unique number */
    private static final AtomicInteger threadCounter = new AtomicInteger(1);

    /** Our thread pool */
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS, runnable -> {
        Thread worker = Executors.defaultThreadFactory().newThread(runnable);
        int threadNumber = threadCounter.getAndUpdate(i -> (i + 1) % 10000);
        worker.setDaemon(true); // don't prevent JVM from exiting
        worker.setName("BLParallelSort-" + threadNumber);
        return worker;
    });

    static {
        // Make sure our executor gets shutdown at program exit, or we will hang.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (executor) {
                // Shut down now
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            }
        }));
    }

    private List<Future<?>> tasks = new ArrayList<>();

    public static void setSeed(long seed) {
        random.setSeed(seed);
    }

    public void parallelSort(int[] array, IntComparator comparator) {
        parallelSort(array, 0, array.length - 1, comparator);

        // Wait for any tasks to complete
        while (true) {
            synchronized (tasks) {
                if (tasks.stream().allMatch(Future::isDone))
                    break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void parallelSort(int[] array, int left, int right, IntComparator comparator) {
        if (left < right) {
            int pivotIndex = partition(array, left, right, comparator);
            if (right - left + 1 < THRESHOLD) {
                // Sort sequentially for small arrays
                IntArrays.quickSort(array, left, pivotIndex, comparator);
                IntArrays.quickSort(array, pivotIndex + 1, right + 1, comparator);
            } else {
                // Sort left and right partitions in parallel using thread pool
                Future<?> f1 = executor.submit(() -> parallelSort(array, left, pivotIndex - 1, comparator));
                Future<?> f2 = executor.submit(() -> parallelSort(array, pivotIndex + 1, right, comparator));
                synchronized (tasks) {
                    tasks.add(f1);
                    tasks.add(f2);
                }
            }
        }
    }

    public int partition(int[] array, int left, int right, IntComparator comparator) {
        int pivotIndex = left + random.nextInt(right - left + 1);
        int pivotValue = array[pivotIndex];
        swap(array, pivotIndex, right);
        int storeIndex = left;
        for (int i = left; i < right; i++) {
            if (comparator.compare(array[i], pivotValue) <= 0) {
                swap(array, i, storeIndex);
                storeIndex++;
            }
        }
        swap(array, storeIndex, right);
        return storeIndex;
    }

    private void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }
}
