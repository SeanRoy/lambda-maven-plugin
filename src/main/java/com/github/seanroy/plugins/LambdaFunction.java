package com.github.seanroy.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * I am a domain class for Lambda Function.
 *
 * @author sean, <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 11/08/16.
 */
@SuppressWarnings({"unused", "ClassWithTooManyFields", "ClassWithTooManyMethods"})
public class LambdaFunction {
    /**
     * <p>
     * The existing Lambda function name whose code you want to replace.
     * </p>
     */
    private String functionName;
    /**
     * <p>
     * A short user-defined function description. AWS Lambda does not use this
     * value. Assign a meaningful description as you see fit.
     * </p>
     */
    private String description;
    /**
     * <p>
     * The function that Lambda calls to begin executing your function. For
     * Node.js, it is the <code>module-name.export</code> value in your
     * function.
     * </p>
     */
    private String handler;
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private Integer memorySize;
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private Integer timeout;
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private String version;
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private List<String> securityGroupIds;
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private List<String> subnetIds;
    /**
     * <p>Lambda function aliases genereted based on publish flag.</p>
     */
    private List<String> aliases;
    
    /**
     * <p>@see {@link AbstractLambdaMojo}</p>
     */
    private Integer keepAlive;
    /**
     * <p>
     * This boolean parameter can be used to request AWS Lambda to update the
     * Lambda function and publish a version as an atomic operation.
     * </p>
     */
    private Boolean publish;
    /**
     * <p>The Amazon Resource Name (ARN) assigned to the function</p>
     */
    private String functionArn;
    /**
     * <p>The triggers that generates events that Lambda responds to</p>
     */
    private List<Trigger> triggers;

    private Map<String, String> environmentVariables;
    
    private String qualifier;

    public LambdaFunction() {
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Integer getMemorySize() {
        return memorySize;
    }

    public void setMemorySize(Integer memorySize) {
        this.memorySize = memorySize;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getSecurityGroupIds() {
        if (securityGroupIds == null) {
            return new ArrayList<>();
        }
        return securityGroupIds;
    }

    public void setSecurityGroupIds(List<String> securityGroupIds) {
        this.securityGroupIds = securityGroupIds;
    }

    public List<String> getSubnetIds() {
        if (subnetIds == null) {
            return new ArrayList<>();
        }
        return subnetIds;
    }

    public void setSubnetIds(List<String> subnetIds) {
        this.subnetIds = subnetIds;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public Boolean isPublish() {
        return publish;
    }

    public void setPublish(boolean publish) {
        this.publish = publish;
    }

    public String getFunctionArn() {
        return functionArn;
    }
    
    public void setKeepAlive(Integer keepAlive) {
        this.keepAlive = keepAlive;
    }
    
    public Integer getKeepAlive() {
        return keepAlive;
    }
    
    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }
    
    public String getQualifier() {
        return qualifier;
    }

    public String getUnqualifiedFunctionArn() {
        return ofNullable(functionArn)
                .map(arn -> arn.replaceAll(functionName + ".*", functionName))
                .orElse(null);
    }

    public List<Trigger> getTriggers() {
        return triggers;
    }

    public void setFunctionArn(String functionArn) {
        this.functionArn = functionArn;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public LambdaFunction withDescription(String description) {
        this.description = description;
        return this;
    }

    public LambdaFunction withFunctionName(String functionName) {
        this.functionName = functionName;
        return this;
    }

    public LambdaFunction withHandler(String handler) {
        this.handler = handler;
        return this;
    }

    public LambdaFunction withMemorySize(Integer memorySize) {
        this.memorySize = memorySize;
        return this;
    }

    public LambdaFunction withSecurityGroupsIds(List<String> securityGroupsIds) {
        this.securityGroupIds = securityGroupsIds;
        return this;
    }

    public LambdaFunction withSubnetIds(List<String> subnetIds) {
        this.subnetIds = subnetIds;
        return this;
    }

    public LambdaFunction withTimeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public LambdaFunction withVersion(String version) {
        this.version = version;
        return this;
    }

    public LambdaFunction withAliases(List<String> aliases) {
        this.aliases = aliases;
        return this;
    }

    public LambdaFunction withPublish(Boolean publish) {
        this.publish = publish;
        return this;
    }

    public LambdaFunction withFunctionArn(String functionArn) {
        this.functionArn = functionArn;
        return this;
    }

    public LambdaFunction withTriggers(List<Trigger> triggers) {
        this.triggers = triggers;
        return this;
    }

    public LambdaFunction withEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
        return this;
    }
    
    public LambdaFunction withKeepAlive(Integer keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }
    
    public LambdaFunction withQualifier(String qualifier) {
        this.qualifier = qualifier;
        return this;
    }
    
    public String getKeepAliveRuleName() {
        return String.format("KEEP-ALIVE-%s", getFunctionName());
    }
    
    public String getKeepAliveScheduleExpression() {
        return String.format("rate(%d %s)", keepAlive, keepAlive > 1 ? "minutes" : "minute");
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        return new StringBuilder("LambdaFunction{")
                .append("functionName='").append(functionName).append('\'')
                .append(", description='").append(description).append('\'')
                .append(", handler='").append(handler).append('\'')
                .append(", memorySize=").append(memorySize)
                .append(", timeout=").append(timeout)
                .append(", version='").append(version).append('\'')
                .append(", securityGroupIds=").append(securityGroupIds)
                .append(", subnetIds=").append(subnetIds)
                .append(", aliases=").append(aliases)
                .append(", publish=").append(publish)
                .append(", triggers=").append(triggers)
                .append(", keepAlive=").append(keepAlive)
                .append(", environmentVariables=").append(environmentVariables)
                .append('}').toString();
    }
}
