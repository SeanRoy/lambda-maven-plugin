package com.github.seanroy.plugins;


import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.function.Function;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import com.amazonaws.services.cloudwatchevents.model.DeleteRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.ListRuleNamesByTargetRequest;
import com.amazonaws.services.cloudwatchevents.model.RemoveTargetsRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionRequest;




/**
 * I am a delete mojo responsible for deleting lambda function configuration and code from AWS.
 *
 * @author Sean N. Roy
 */
@Mojo(name = "delete-lambda")
public class DeleteLambdaMojo extends AbstractLambdaMojo {

    /**
     * The entry point into the AWS lambda function.
     */
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            lambdaFunctions.forEach(context -> {
                try {
                    deleteTriggers.andThen(deleteFunction).apply(
                            context.withFunctionArn(lambdaClient.getFunction(new GetFunctionRequest()
                                .withFunctionName(context.getFunctionName())).getConfiguration().getFunctionArn()));                      
                } catch (Exception e) {
                    getLog().error(e.getMessage());
                }
            });
        } catch (Exception e) {
            getLog().error(e.getMessage(), e);
        }
    }
    
    private Function<LambdaFunction, LambdaFunction> deleteDynamoDBTrigger = lambdaFunction -> {return lambdaFunction;};
    private Function<LambdaFunction, LambdaFunction> deleteKinesisTrigger = lambdaFunction -> {return lambdaFunction;};
    private Function<LambdaFunction, LambdaFunction> deleteSNSTrigger = lambdaFunction -> {return lambdaFunction;};
    private Function<LambdaFunction, LambdaFunction> deleteAlexaSkillsTrigger = lambdaFunction -> {return lambdaFunction;};
    
    
    /*
     * Delete cloudwatch event rules.
     */
    private Function<LambdaFunction, LambdaFunction> deleteCloudWatchEventRules = lambdaFunction -> {
        // Get the list of cloudwatch event rules defined for this function (if any).
        List<String> existingRuleNames = cloudWatchEventsClient.listRuleNamesByTarget(new ListRuleNamesByTargetRequest()
            .withTargetArn(lambdaFunction.getFunctionArn())).getRuleNames();
        
        existingRuleNames.stream().forEach(ern -> {
            getLog().info("    Deleting CloudWatch Event Rule: " + ern);
            cloudWatchEventsClient.removeTargets(new RemoveTargetsRequest()
                .withIds("1")
                .withRule(ern));
            try {
                cloudWatchEventsClient.deleteRule(new DeleteRuleRequest().withName(ern));
            } catch (Exception e) {
                getLog().info("    Could not delete orphaned rule: " + e.getMessage());
            }
        });
        
        return lambdaFunction;
    };

    /**
     * Deletes the lambda function from AWS Lambda and removes the function code
     * package from S3.
     * <p>
     * TODO: Make this more sophisticated by checking for the existence of the
     * TODO: resources first, or reacting to the ResourceNotFoundException. I
     * TODO: prefer the first option.
     * </p>
     *
     * @param functionName to delete
     * @throws Exception the exception from AWS API
     */
    private Function<LambdaFunction, LambdaFunction> deleteFunction = context -> {
        String functionName = context.getFunctionName();
        
        // Delete Lambda Function
        DeleteFunctionRequest dfr = new DeleteFunctionRequest().withFunctionName(functionName);

        lambdaClient.deleteFunction(dfr);
        getLog().info("Lambda function " + functionName + " successfully deleted.");

        s3Client.deleteObject(s3Bucket, fileName);
        getLog().info("Lambda function code successfully removed from S3.");
        
        return context;
    };
    
    /**
     * For every Integration, ie Trigger, andThen a delete function for it here.
     */
    private Function<LambdaFunction, LambdaFunction> deleteTriggers = lambdaFunction -> {        
        return deleteCloudWatchEventRules
                .andThen(deleteDynamoDBTrigger)
                .andThen(deleteKinesisTrigger)
                .andThen(deleteSNSTrigger)
                .andThen(deleteAlexaSkillsTrigger)
                .apply(lambdaFunction);
    };
}
