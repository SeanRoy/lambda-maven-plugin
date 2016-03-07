package com.github.seanroy.plugins;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 * @author Sean N. Roy
 */
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import com.amazonaws.services.lambda.model.DeleteFunctionRequest;

@Mojo(name = "delete-lambda")
public class DeleteLambduhMojo extends AbstractLambduhMojo {
    /**
     * The entry point into the AWS lambda function.
     */
    public void execute() throws MojoExecutionException {
        super.execute();
        
        try {
            deleteFunction();
        } catch ( Exception e ) {
            getLog().error(e.getMessage(), e);
        }
    }
    
    /**
     * Deletes the lambda function from AWS Lambda and removes the function code
     * package from S3.
     * 
     * TODO: Make this more sophisticated by checking for the existence of the 
     * TODO: resources first, or reacting to the ResourceNotFoundException. I
     * TODO: prefer the first option.
     * @throws Exception
     */
    private void deleteFunction() throws Exception {
        // Delete Lambda Function
        DeleteFunctionRequest dfr = new DeleteFunctionRequest()
        .withFunctionName(functionName);
        
        lambdaClient.deleteFunction(dfr);
        getLog().info("Lambda function " + functionName + " successfully deleted.");
        
        s3Client.deleteObject(s3Bucket, fileName);
        getLog().info("Lambda function code successfully removed from S3.");
    }
}
