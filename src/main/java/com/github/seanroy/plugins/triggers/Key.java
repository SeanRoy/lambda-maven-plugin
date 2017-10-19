package com.github.seanroy.plugins.triggers;

public class Key {
    public Key() {
        
    }
    
    public FilterRule[] getFilterRules() {
        return filterRules;
    }

    public void setFilterRules(FilterRule[] filterRules) {
        this.filterRules = filterRules;
    }
    
    private FilterRule [] filterRules;   
}