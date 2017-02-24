package com.puppetlabs.trapperkeeper.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;

import java.util.Set;

// Takes in a whitelist of strings to match against
public class AllowedNamesMetricFilter implements MetricFilter{
    private final Set<String> allowedMetricNames;

    public AllowedNamesMetricFilter(Set<String> allowedMetricNames){
        this.allowedMetricNames = allowedMetricNames;
    }

    @Override
    public boolean matches(String name, Metric metric) {
        if (allowedMetricNames.isEmpty()) {
            return true;
        } else return allowedMetricNames.contains(name);
    }
}
