package com.github.seanroy.plugins;

public class LambduhFunctionContext {
    private String functionName;
    private String handlerName;
    
    public LambduhFunctionContext(String functionName, String handlerName) {
        this.functionName = functionName;
        this.handlerName = handlerName;
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
}
