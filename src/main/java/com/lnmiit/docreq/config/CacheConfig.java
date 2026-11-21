package com.lnmiit.docreq.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Two read-mostly caches (Caffeine, configured in application.yml under spring.cache):
 * <ul>
 *   <li>{@link #PRINCIPALS}: user id to {@code Optional<AuthUser>}. Without it every authenticated
 *       request costs a users query in {@code JwtAuthFilter}, just to check the account is still active.</li>
 *   <li>{@link #OFFERED_TYPES}: the document types a student can request, read on every form load.</li>
 * </ul>
 * Writes evict the affected entry after their transaction commits, so a deactivated user is refused on the
 * very next request (FR-028) and a withdrawn document type disappears at once (TC-08). The TTL is only a
 * backstop: it bounds staleness if the app is ever scaled to several instances, each with its own cache.
 *
 * <p>The cache advice runs outside the transaction advice, so a cache hit does not open a transaction.
 * Set {@code spring.cache.type=none} to switch caching off (the benchmark does this for its baseline).
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig {

    public static final String PRINCIPALS = "principals";
    public static final String OFFERED_TYPES = "offeredTypes";

    /** Evicts cache entries once the surrounding transaction has committed (or at once if there is none). */
    @Component
    public static class CacheEvictor {

        private final CacheManager caches;

        public CacheEvictor(CacheManager caches) {
            this.caches = caches;
        }

        public void principal(Long userId) {
            afterCommit(() -> evict(PRINCIPALS, userId));
        }

        public void offeredTypes() {
            afterCommit(() -> clear(OFFERED_TYPES));
        }

        private void evict(String name, Object key) {
            Cache cache = caches.getCache(name);
            if (cache != null) cache.evict(key);
        }

        private void clear(String name) {
            Cache cache = caches.getCache(name);
            if (cache != null) cache.clear();
        }

        /*
         * Evicting before commit would let a concurrent reader put the old row straight back into the
         * cache, so the eviction waits for the commit.
         */
        private static void afterCommit(Runnable action) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            } else {
                action.run();
            }
        }
    }
}
