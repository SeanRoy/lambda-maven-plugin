package com.github.seanroy.plugins;

import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest; 
import com.amazonaws.services.lambda.model.Runtime;

import com.amazonaws.services.lambda.model.Runtime;

public class LambduhFunctionContext {
    private String functionName;
    private String description;
    private Runtime runtime;
    private String handlerName;
    private CreateEventSourceMappingRequest createEventSourceMappingRequest;
    
    public LambduhFunctionContext() {
    }
    
    public CreateEventSourceMappingRequest getCreateEventSourceMappingRequest() {
        return createEventSourceMappingRequest;
    }

    public LambduhFunctionContext withCreateEventSourceMappingRequest(
            CreateEventSourceMappingRequest createEventSourceMappingRequest) {
        this.createEventSourceMappingRequest = createEventSourceMappingRequest;
        
        return this;
    }

    public String getFunctionName() {
        return functionName;
    }
    public LambduhFunctionContext withFunctionName(String functionName) {
        this.functionName = functionName;    
        return this;
    }
    public String getHandlerName() {
        return handlerName;
    }
    public LambduhFunctionContext withHandlerName(String handlerName) {
        this.handlerName = handlerName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public LambduhFunctionContext withDescription(String description) {
        this.description = description;
        return this;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public LambduhFunctionContext withRuntime(String runtime) {
        this.runtime = Runtime.fromValue(runtime.toLowerCase());
        return this;
    }
}
