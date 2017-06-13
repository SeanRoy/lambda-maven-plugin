package com.github.seanroy.plugins;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionRequest;




/**
 * I am a delete mojo responsible for deleteing lambda function configuration and code from AWS.
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
                context.setFunctionArn(lambdaClient.getFunction(new GetFunctionRequest()
                    .withFunctionName(context.getFunctionName())).getConfiguration().getFunctionArn());
                
                try {
                    deleteFunction(context);
                    deleteTriggers(context);
                    
                } catch (Exception e) {
                    getLog().error(e.getMessage());
                }
            });
        } catch (Exception e) {
            getLog().error(e.getMessage(), e);
        }
    }
    
    /*
     * TODO
     */
    private void deleteTriggers(LambdaFunction context) {
        context.getTriggers().forEach(trigger -> {
            if (TRIG_INT_LABEL_CLOUDWATCH_EVENTS.equals(trigger.getIntegration())) {
                
            } else if (TRIG_INT_LABEL_DYNAMO_DB.equals(trigger.getIntegration())) {
                
            } else if (TRIG_INT_LABEL_KINESIS.equals(trigger.getIntegration())) {
               
            } else if (TRIG_INT_LABEL_SNS.equals(trigger.getIntegration())) {
                
            } else if (TRIG_INT_LABEL_ALEXA_SK.equals(trigger.getIntegration())) {
                
            } else {
                getLog().error("Unknown integration for trigger " + trigger.getIntegration() + ". Correct your configuration");
            }
        });
    }

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
    private void deleteFunction(LambdaFunction context) throws Exception {
        String functionName = context.getFunctionName();
        
        // Delete Lambda Function
        DeleteFunctionRequest dfr = new DeleteFunctionRequest().withFunctionName(functionName);

        lambdaClient.deleteFunction(dfr);
        getLog().info("Lambda function " + functionName + " successfully deleted.");

        s3Client.deleteObject(s3Bucket, fileName);
        getLog().info("Lambda function code successfully removed from S3.");
    }
}
