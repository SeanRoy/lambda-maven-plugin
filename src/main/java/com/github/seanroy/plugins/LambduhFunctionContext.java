package com.github.seanroy.plugins;

import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest;

public class LambduhFunctionContext {
    private String functionName;
    private String handlerName;
    private CreateEventSourceMappingRequest createEventSourceMappingRequest;
    
    public LambduhFunctionContext(String functionName, String handlerName) {
        this.functionName = functionName;
        this.handlerName = handlerName;
    }
    
    public CreateEventSourceMappingRequest getCreateEventSourceMappingRequest() {
        return createEventSourceMappingRequest;
    }

    public void setCreateEventSourceMappingRequest(
            CreateEventSourceMappingRequest createEventSourceMappingRequest) {
        this.createEventSourceMappingRequest = createEventSourceMappingRequest;
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
