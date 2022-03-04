package nl.inl.blacklab.server.search;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCacheEntryFromFuture;
import nl.inl.blacklab.server.config.BLSConfig;

public class ResultsCache implements SearchCache {
    private static final Logger logger = LogManager.getLogger(ResultsCache.class);
    private static final String CACHE_NAME_FOR_METRICS = "blacklab-results-cache";
    private final ExecutorService threadPool;
    private final AsyncLoadingCache<Search<? extends SearchResult>, SearchResult> searchCache;
    private final ConcurrentHashMap<Search<? extends SearchResult>, Future<? extends SearchResult>> runningJobs = new ConcurrentHashMap<>();


    public static class CacheEntryWithResults<T extends SearchResult> extends SearchCacheEntry<T> {

        private final T results;

        public CacheEntryWithResults(T results) {
            this.results = results;
        }
        @Override
        public boolean wasStarted() {
            return true;
        }

        @Override
        public void start() {
        }

        @Override
        public long timeUserWaitedMs() {
            return -1;
        }

        @Override
        public boolean threwException() {
            return false;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return results;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return results;
        }

        @Override
        public T peek() {
            return results;
        }
    }

    public ResultsCache(BLSConfig config, ExecutorService threadPool)  {
        this.threadPool = threadPool;

        CacheLoader<Search<? extends SearchResult>, SearchResult> cacheLoader = new CacheLoader<Search<? extends SearchResult>, SearchResult>() {
            @Override
            public @Nullable SearchResult load(Search<?> search) throws Exception {
                ThreadContext.put("requestId", Integer.toHexString(search.hashCode()));
                long start = System.currentTimeMillis();
                Future<? extends SearchResult> job;
                if (runningJobs.containsKey(search)) {
                    job = runningJobs.get(search);
                } else {
                    job = ResultsCache.this.threadPool.submit( () -> search.executeInternal() );
                    runningJobs.put(search, job);
                }
                SearchResult searchResult = job.get();
                logger.warn("Search time is: {}", System.currentTimeMillis() - start);
                runningJobs.remove(search);
                return searchResult;
            }
        };

        int maxSize = config.getCache().getMaxNumberOfJobs();
        logger.info("Creating cache with maxSize:{}", maxSize);
        searchCache = Caffeine.newBuilder()
            .recordStats()
            .maximumSize(maxSize)
            .initialCapacity(maxSize / 10)
            .buildAsync(cacheLoader);
        CaffeineCacheMetrics.monitor(Metrics.globalRegistry, searchCache, CACHE_NAME_FOR_METRICS);
        Metrics.globalRegistry.gaugeMapSize("blacklab-job-queue", Tags.empty(), runningJobs);
    }
    @Override
    public <T extends SearchResult> SearchCacheEntry<T> getAsync(final Search<T> search, final boolean allowQueue) {
        try {
            CompletableFuture<SearchResult> resultsFuture = searchCache.get(search);
            SearchCacheEntryFromFuture cacheEntry = new SearchCacheEntryFromFuture(resultsFuture, search);
            return cacheEntry;
        }catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    @Override
    public <T extends SearchResult> SearchCacheEntry<T> remove(Search<T> search) {
        if (searchCache.asMap().containsKey(search)) {
            SearchResult searchResult = searchCache.synchronous().get(search);
            searchCache.synchronous().invalidate(search);
            return new CacheEntryWithResults(searchResult);
        }
        return null;
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        searchCache.asMap().keySet().removeIf(s -> s.queryInfo().index() == index);
    }

    @Override
    public void clear(boolean cancelRunning) {
        searchCache.synchronous().invalidateAll();
    }

    @Override
    public void cleanup() {
        clear(true);
    }

    @Override
    public Map<String, Object> getCacheStatus() {
        return null;
    }

    @Override
    public List<Map<String, Object>> getCacheContent(boolean includeDebugInfo) {
        return null;
    }
}
