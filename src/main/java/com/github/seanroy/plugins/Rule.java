package com.github.seanroy.plugins;

/**
 * I am a Rule.
 *
 * @author <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 29/08/16.
 */
public class Rule {
    private String name;
    private String description;
    private String scheduleExpression;

    public Rule() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScheduleExpression() {
        return scheduleExpression;
    }

    public void setScheduleExpression(String scheduleExpression) {
        this.scheduleExpression = scheduleExpression;
    }

    public Rule withName(String name) {
        this.name = name;
        return this;
    }

    public Rule withDescription(String description) {
        this.description = description;
        return this;
    }

    public Rule withScheduleExpression(String scheduleExpression) {
        this.scheduleExpression = scheduleExpression;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder("Rule{")
                .append("name='").append(name).append('\'')
                .append(", description='").append(description).append('\'')
                .append(", scheduleExpression='").append(scheduleExpression).append('\'')
                .append('}').toString();
    }
}
