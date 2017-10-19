package com.github.seanroy.plugins.triggers;

/**
 * I am a Trigger.
 *
 * @author <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 12/10/16.
 */
public class Trigger {
    // By now can be "DynamoDB" or "CloudWatch Events - Schedule"
    private String integration;

    // Support for DynamoDB
    private String dynamoDBTable;
    private Integer batchSize;

    // Support for Kinesis Streams
    private String kinesisStream;

    /**
     * <p> Starting position. </p>
     */
    private String startingPosition;

    // Support for CloudWatch Events - Schedule
    private String ruleName;
    private String ruleDescription;
    private String scheduleExpression;

    // Support for SNS
    private String SNSTopic;
    
    // Support for Lex Bots
    private String lexBotName;

    // After create Trigger gets own arn
    private String triggerArn;
    
    private Bucket [] buckets;

    private Boolean enabled;

    public Trigger() {
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public String getDynamoDBTable() {
        return dynamoDBTable;
    }

    public void setDynamoDBTable(String dynamoDBTable) {
        this.dynamoDBTable = dynamoDBTable;
    }

    public String getKinesisStream() {
        return kinesisStream;
    }

    public void setKinesisStream(String kinesisStream) {
        this.kinesisStream = kinesisStream;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public String getStartingPosition() {
        return startingPosition;
    }

    public void setStartingPosition(String startingPosition) {
        this.startingPosition = startingPosition;
    }


    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public void setRuleDescription(String ruleDescription) {
        this.ruleDescription = ruleDescription;
    }

    public String getScheduleExpression() {
        return scheduleExpression;
    }

    public void setScheduleExpression(String scheduleExpression) {
        this.scheduleExpression = scheduleExpression;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTriggerArn() {
        return triggerArn;
    }

    public void setTriggerArn(String triggerArn) {
        this.triggerArn = triggerArn;
    }

    public String getSNSTopic() {
        return SNSTopic;
    }

    public void setSNSTopic(String SNSTopic) {
        this.SNSTopic = SNSTopic;
    }
    
    public void setLexBotName(String arn) {
        this.lexBotName = arn;
    }
    
    public String getLexBotName() {
        return lexBotName;
    }
    
    public void setBuckets(Bucket [] buckets) {
        this.buckets = buckets;
    }
    
    public Bucket [] getBuckets() {
        return buckets;
    }

    public Trigger withIntegration(String integration) {
        this.integration = integration;
        return this;
    }

    public Trigger withDynamoDBTable(String dynamoDBTable) {
        this.dynamoDBTable = dynamoDBTable;
        return this;
    }

    public Trigger withKinesisStream(String kinesisStream) {
        this.kinesisStream = kinesisStream;
        return this;
    }

    public Trigger withBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public Trigger withRuleName(String ruleName) {
        this.ruleName = ruleName;
        return this;
    }

    public Trigger withDescription(String ruleDescription) {
        this.ruleDescription = ruleDescription;
        return this;
    }

    public Trigger withScheduleExpression(String scheduleExpression) {
        this.scheduleExpression = scheduleExpression;
        return this;
    }

    public Trigger withTriggerArn(String triggerArn) {
        this.triggerArn = triggerArn;
        return this;
    }

    public Trigger withSNSTopic(String sNSTopic) {
        this.SNSTopic = sNSTopic;
        return this;
    }
    
    public Trigger withLexBotName(String arn) {
        this.lexBotName = arn;
        return this;
    }
    
    public Trigger withBuckets(Bucket [] buckets) {
        this.buckets = buckets;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder("Trigger{")
                .append("integration='").append(integration).append('\'')
                .append(", dynamoDBTable='").append(dynamoDBTable).append('\'')
                .append(", kinesisStream='").append(kinesisStream).append('\'')
                .append(", batchSize=").append(batchSize)
                .append(", startingPosition='").append(startingPosition).append('\'')
                .append(", ruleName='").append(ruleName).append('\'')
                .append(", ruleDescription='").append(ruleDescription).append('\'')
                .append(", scheduleExpression='").append(scheduleExpression).append('\'')
                .append(", SNSTopic='").append(SNSTopic).append('\'')
                .append(", triggerArn='").append(triggerArn).append('\'')
                .append(", lextBotName='").append(lexBotName).append('\'')
                .append(", enabled=").append(enabled)
                .append('}').toString();
    }
}
