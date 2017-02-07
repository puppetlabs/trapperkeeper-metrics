package com.puppetlabs.enterprise;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;

import java.util.Set;

// Takes in a whitelist of strings to match against
public class PEMetricFilter implements MetricFilter{
    private final Set<String> allowedMetricNames;

    public PEMetricFilter(Set<String> allowedMetricNames){
        this.allowedMetricNames = allowedMetricNames;
    }

    @Override
    public boolean matches(String name, Metric metric) {
        if (allowedMetricNames.isEmpty()) {
            return true;
        } else return allowedMetricNames.contains(name);
    }
}
