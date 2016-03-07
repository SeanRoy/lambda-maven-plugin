package com.github.seanroy.plugins;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 * @author Sean N. Roy
 */
import java.io.File;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.s3.AmazonS3Client;

@Mojo(name = "delete-lambda")
public class DeleteLambduhMojo extends AbstractMojo {
    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    private String accessKey;

    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    private String secretKey;

    @Parameter(required = true, defaultValue = "${functionCode}")
    private String functionCode;

    @Parameter(alias = "region", property = "region", defaultValue = "us-east-1")
    private String regionName;

    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    private String s3Bucket;

    @Parameter(property = "description", defaultValue = "")
    private String description;

    @Parameter(required = true, defaultValue = "${lambdaRoleArn}")
    private String lambdaRoleArn;

    @Parameter(property = "functionName", defaultValue = "${functionName}")
    private String functionName;

    @Parameter(required = true, defaultValue = "${handler}")
    private String handler;

    @Parameter(property = "runtime", defaultValue = "Java8")
    private Runtime runtime;

    /**
     * Lambda function execution timeout. Defaults to maximum allowed.
     */
    @Parameter(property = "timeout", defaultValue = "60")
    private int timeout;

    @Parameter(property = "memorySize", defaultValue = "128")
    private int memorySize;

    private String fileName;
    private Region region;

    private AWSCredentials credentials;
    private AmazonS3Client s3Client;
    private AWSLambdaClient lambdaClient;

    /**
     * The entry point into the AWS lambda function.
     */
    public void execute() throws MojoExecutionException {
        // TODO: DRY this up, this code is repeated in LambduhMojo // 
        DefaultAWSCredentialsProviderChain defaultChain = 
                new DefaultAWSCredentialsProviderChain();
        
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        }
        else if (defaultChain.getCredentials()!=null)
        {
            credentials = defaultChain.getCredentials();
        }

        s3Client = (credentials==null) ? 
                new AmazonS3Client():new AmazonS3Client(credentials);
        lambdaClient = (credentials==null) ?
                new AWSLambdaClient():new AWSLambdaClient(credentials);


        region = Region.getRegion(Regions.fromName(regionName));
        lambdaClient.setRegion(region);
        /////////////// DRY ///////////////////////////////////////
        
        try {
            deleteFunction();
        } catch ( Exception e ) {
            getLog().error(e.getMessage(), e);
        }
        
    }
    
    /**
     * Deletes the lambda function from AWS Lambda and removes the function code
     * package from S3.
     * @throws Exception
     */
    private void deleteFunction() throws Exception {
        // Delete Lambda Function
        DeleteFunctionRequest dfr = new DeleteFunctionRequest()
        .withFunctionName(functionName);
      
        lambdaClient.deleteFunction(dfr);
        getLog().info("Lambda function " + functionName + " successfully deleted.");
        
        // Remove function code from S3, if it exists.
        String pattern = Pattern.quote(File.separator);
        String[] pieces = functionCode.split(pattern);
        fileName = pieces[pieces.length - 1];
        
        s3Client.deleteObject(s3Bucket, fileName);
        getLog().info("Lambda function code successfully removed from S3.");
    }
}
