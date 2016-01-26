package com.github.seanroy.plugins;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 * @author Sean N. Roy
 */
import java.io.File;
import java.io.FileInputStream;
import java.util.regex.Pattern;

import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.apache.commons.codec.digest.DigestUtils;
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
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.s3.AmazonS3Client;

@Mojo(name = "deploy-lambda")
public class LambduhMojo extends AbstractMojo {
    final Logger logger = LoggerFactory.getLogger(LambduhMojo.class);

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
        DefaultAWSCredentialsProviderChain defaultChain = new DefaultAWSCredentialsProviderChain();
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        }
        else if (defaultChain.getCredentials()!=null)
        {
            credentials = defaultChain.getCredentials();
        }

        s3Client = (credentials==null)?new AmazonS3Client():new AmazonS3Client(credentials);
        lambdaClient = (credentials==null)?new AWSLambdaClient():new AWSLambdaClient(credentials);


        region = Region.getRegion(Regions.fromName(regionName));
        lambdaClient.setRegion(region);

        String pattern = Pattern.quote(File.separator);
        String[] pieces = functionCode.split(pattern);
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
        String bucket = getBucket();
        File file = new File(functionCode);

        String localmd5 = DigestUtils.md5Hex(new FileInputStream(file));
        logger.debug(String.format("Local file's MD5 hash is %s.", localmd5));

        // See if the JAR is already current; if it is, let's not re-upload it.
        boolean remoteIsCurrent = false;
        try {
            ObjectMetadata currentObj = s3Client.getObjectMetadata(bucket, fileName);
            logger.info(String.format("Object exists in S3 with MD5 hash %s.", currentObj.getETag()));
            remoteIsCurrent = localmd5.equals(currentObj.getETag());
        }
        catch (AmazonClientException ace) {
            logger.info("Object does not exist in S3 or we cannot access it.");
        }

        if (remoteIsCurrent) {
            logger.info("The package already in S3 is up-to-date.");
        }
        else {
            logger.info("Uploading " + functionCode + " to AWS S3 bucket "
                    + s3Bucket);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentMD5(localmd5);
            metadata.setContentLength(file.length());
            s3Client.putObject(s3Bucket, fileName, new FileInputStream(file), metadata);
            logger.info("Upload complete");
        }
    }

    /**
     * Attempts to return an existing bucket named <code>s3Bucket</code> if it
     * exists. If it does not exist, it attempts to create it.
     * 
     * @return An AWS S3 bucket with name <code>s3Bucket</code>, or raises an exception
     */
    private String getBucket() {
        if (bucketExists()) {
            return s3Bucket;
        }
        else {
            try {
                s3Client.createBucket(s3Bucket,
                        com.amazonaws.services.s3.model.Region.US_Standard);
                logger.info("Created bucket " + s3Bucket);
                return s3Bucket;
            } catch (AmazonServiceException ase) {
                logger.error(ase.getMessage());
                throw ase;
            } catch (AmazonClientException ace) {
                logger.error(ace.getMessage());
                throw ace;
            }
        }
    }

    private boolean bucketExists() {
        try {
            s3Client.headBucket(new HeadBucketRequest(s3Bucket));
            return true;
        }
        catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            else {
                // Some other error, but the bucket appears to exist.
                logger.error(String.format("Got status code %d for bucket named '%s'", e.getStatusCode(), s3Bucket));
                return true;
            }
        }
    }
}
