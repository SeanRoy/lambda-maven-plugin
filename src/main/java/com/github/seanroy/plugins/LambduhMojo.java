package com.github.seanroy.plugins;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 * @author Sean N. Roy
 */
import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;

@Mojo(name = "deploy-lambda")
public class LambduhMojo extends AbstractMojo {
    final Logger logger = LoggerFactory.getLogger(LambduhMojo.class);

    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    private String accessKey;

    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    private String secretKey;

    @Parameter(required = true, defaultValue = "${functionCode}")
    private String functionCode;

    @Parameter(property = "region", defaultValue = "us-east-1")
    private String regionName;

    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    private String s3Bucket;

    @Parameter(property = "description", defaultValue = "")
    private String description;

    @Parameter(required = true, defaultValue = "${lambdaRoleArn}")
    private String lambdaRoleArn;

    @Parameter(property = "functionName", defaultValue = "function-1")
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
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
            s3Client = new AmazonS3Client(credentials);
            lambdaClient = new AWSLambdaClient(credentials);
        } else {
            s3Client = new AmazonS3Client();
            lambdaClient = new AWSLambdaClient();
        }

        region = Region.getRegion(Regions.fromName(regionName));
        lambdaClient.setRegion(region);

        String[] pieces = functionCode.split(File.separator);
        fileName = pieces[pieces.length - 1];

        try {
            uploadJarToS3();
            deployLambdaFunction();
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.trace(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    /**
     * Makes a create function call on behalf of the caller, deploying the
     * function code to AWS lambda.
     * 
     * @return A CreateFunctionResult indicating the success or failure of the
     *         request.
     */
    private CreateFunctionResult createFunction() {
        CreateFunctionRequest createFunctionRequest = new CreateFunctionRequest();
        createFunctionRequest.setDescription(description);
        createFunctionRequest.setRole(lambdaRoleArn);
        createFunctionRequest.setFunctionName(functionName);
        createFunctionRequest.setHandler(handler);
        createFunctionRequest.setRuntime(runtime);
        createFunctionRequest.setTimeout(timeout);
        createFunctionRequest.setMemorySize(memorySize);

        FunctionCode functionCode = new FunctionCode();
        functionCode.setS3Bucket(s3Bucket);
        functionCode.setS3Key(fileName);
        createFunctionRequest.setCode(functionCode);

        return lambdaClient.createFunction(createFunctionRequest);
    }

    /**
     * Attempts to delete an existing function of the same name then deploys the
     * function code to AWS Lambda. TODO: Attempt to do an update with
     * versioning if the function already TODO: exists.
     */
    private void deployLambdaFunction() {
        // Attempt to delete it first
        try {
            DeleteFunctionRequest deleteRequest = new DeleteFunctionRequest();
            // Why the hell didn't they make this a static method?
            deleteRequest = deleteRequest.withFunctionName(functionName);
            lambdaClient.deleteFunction(deleteRequest);
        } catch (Exception ignored) {
            // function didn't exist in the first place.
        }

        CreateFunctionResult result = createFunction();
        logger.info("Function deployed: " + result.getFunctionArn());
    }

    /**
     * The Lambda function will be deployed from AWS S3. This method uploads the
     * function code to S3 in preparation of deployment.
     * 
     * @throws Exception
     */
    private void uploadJarToS3() throws Exception {
        Bucket bucket = getBucket();

        if (bucket != null) {
            File file = new File(functionCode);

            logger.info("Uploading " + functionCode + " to AWS S3 bucket "
                    + s3Bucket);
            s3Client.putObject(s3Bucket, fileName, file);
            logger.info("Upload complete");

        } else {
            logger.error("Failed to create bucket " + s3Bucket
                    + "try running maven with -X to get full " + "debug output");
        }
    }

    /**
     * Attempts to return an existing bucket named <code>s3Bucket</code> if it
     * exists. If it does not exist, it attempts to create it.
     * 
     * @return An AWS S3 bucket with name <code>s3Bucket</code>
     */
    private Bucket getBucket() {
        Bucket bucket = null;

        try {
            bucket = s3Client.createBucket(s3Bucket,
                    com.amazonaws.services.s3.model.Region.US_Standard);
            logger.info("Created bucket " + s3Bucket);
        } catch (AmazonServiceException ase) {
            logger.error(ase.getMessage());
        } catch (AmazonClientException ace) {
            logger.error(ace.getMessage());
        }

        return bucket;
    }
}
