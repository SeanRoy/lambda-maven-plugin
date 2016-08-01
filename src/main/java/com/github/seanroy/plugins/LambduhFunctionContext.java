package com.github.seanroy.plugins;

import com.amazonaws.services.lambda.model.Runtime;

public class LambduhFunctionContext {
    private String functionName;
    private String description;
    private Runtime runtime;
    private String handlerName;
    
    public LambduhFunctionContext(String functionName, String description, String runtime, String handlerName) {
        this.functionName = functionName;
        this.handlerName = handlerName;
        this.description = description;
        setRuntime(runtime);
    }
    
    public String getFunctionName() {
        return functionName;
    }
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    public String getHandlerName() {
        return handlerName;
    }
    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = Runtime.fromValue(runtime.toLowerCase());
    }
}
