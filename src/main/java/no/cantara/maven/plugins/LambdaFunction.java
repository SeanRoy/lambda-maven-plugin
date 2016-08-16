package no.cantara.maven.plugins;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:kgrodzicki@gmail.com">Krzysztof Grodzicki</a> 11/08/16.
 */
public class LambdaFunction {
    private String functionName;
    private String description;
    private String handler;
    private Integer memorySize;
    private Integer timeout;
    private String version;
    private List<String> securityGroupsIds;
    private List<String> subnetIds;
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
        final StringBuilder sb = new StringBuilder("LambdaFunction{");
        sb.append("functionName='").append(functionName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", handler='").append(handler).append('\'');
        sb.append(", memorySize=").append(memorySize);
        sb.append(", timeout=").append(timeout);
        sb.append(", version='").append(version).append('\'');
        sb.append(", securityGroupsIds=").append(securityGroupsIds);
        sb.append(", subnetIds=").append(subnetIds);
        sb.append(", aliases=").append(aliases);
        sb.append('}');
        return sb.toString();
    }
}
