package com.github.seanroy.plugins.triggers;

public class FilterRule {
    public FilterRule() {
        
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    private String name;
    private String value;
}
