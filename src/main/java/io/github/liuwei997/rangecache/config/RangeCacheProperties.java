package io.github.liuwei997.rangecache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rangecache")
public class RangeCacheProperties {

    /** Whether annotation-driven range caching is enabled. */
    private boolean enabled = true;

    /** Maximum missing intervals fetched separately before using one full query. */
    private int maxDeltaQueries = 5;

    /** Maximum logical series retained by the local LRU store. */
    private int maximumSeries = 512;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxDeltaQueries() {
        return maxDeltaQueries;
    }

    public void setMaxDeltaQueries(int maxDeltaQueries) {
        this.maxDeltaQueries = maxDeltaQueries;
    }

    public int getMaximumSeries() {
        return maximumSeries;
    }

    public void setMaximumSeries(int maximumSeries) {
        this.maximumSeries = maximumSeries;
    }

    public void validate() {
        if (maxDeltaQueries < 0) {
            throw new IllegalArgumentException("rangecache.max-delta-queries must be at least 0");
        }
        if (maximumSeries < 1) {
            throw new IllegalArgumentException("rangecache.maximum-series must be at least 1");
        }
    }
}
