package no.cantara.maven.plugins;

import java.util.ArrayList;
import java.util.List;

/**
 * I am a domain class for Lambda Function.
 *
 * @author sean, <a href="mailto:kgrodzicki@gmail.com">Krzysztof Grodzicki</a> 11/08/16.
 */
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
     * <p>@see {@link AbstractLambduhMojo}</p>
     */
    private Integer memorySize;
    /**
     * <p>@see {@link AbstractLambduhMojo}</p>
     */
    private Integer timeout;
    /**
     * <p>@see {@link AbstractLambduhMojo}</p>
     */
    private String version;
    /**
     * <p>@see {@link AbstractLambduhMojo}</p>
     */
    private List<String> securityGroupsIds;
    /**
     * <p>@see {@link AbstractLambduhMojo}</p>
     */
    private List<String> subnetIds;
    /**
     * <p>Lambda function aliases genereted based on current Alias.</p>
     */
    private List<String> aliases;

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

    public List<String> getSecurityGroupsIds() {
        if (securityGroupsIds == null) {
            return new ArrayList<>();
        }
        return securityGroupsIds;
    }

    public void setSecurityGroupsIds(List<String> securityGroupsIds) {
        this.securityGroupsIds = securityGroupsIds;
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
        this.securityGroupsIds = securityGroupsIds;
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

    @Override
    public String toString() {
        return new StringBuilder("LambdaFunction{")
                .append("functionName='").append(functionName).append('\'')
                .append(", description='").append(description).append('\'')
                .append(", handler='").append(handler).append('\'')
                .append(", memorySize=").append(memorySize)
                .append(", timeout=").append(timeout)
                .append(", version='").append(version).append('\'')
                .append(", securityGroupsIds=").append(securityGroupsIds)
                .append(", subnetIds=").append(subnetIds)
                .append(", aliases=").append(aliases)
                .append('}').toString();
    }
}
