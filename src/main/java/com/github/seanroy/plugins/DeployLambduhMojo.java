package com.github.seanroy.plugins;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 * @author Sean N. Roy
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.scannotation.AnnotationDB;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.github.seanroy.annotations.LambduhFunction;


@Mojo(name = "deploy-lambda")
public class DeployLambduhMojo extends AbstractLambduhMojo {
    /**
     * The entry point into the AWS lambda function.
     */
    public void execute() throws MojoExecutionException {
        super.execute();

        try {
            uploadJarToS3();
            
            lambduhFunctionContexts.forEach( 
                    context -> deployLambdaFunction(context));
                                                    
        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException(e.getMessage());
        }
    }

    /**
     * Makes a get function call on behalf of the caller, returning the function
     * config and info.
     *
     * @throws ResourceNotFoundException if requested function does not exist
     *
     * @return A GetFunctionResult containing the returned function info.
     */
    private GetFunctionResult getFunction(LambduhFunctionContext context) {
        GetFunctionRequest getFunctionRequest = new GetFunctionRequest();
        getFunctionRequest.setFunctionName(context.getFunctionName());

        return lambdaClient.getFunction(getFunctionRequest);
    }

    /**
     * Makes a create function call on behalf of the caller, deploying the
     * function code to AWS lambda.
     *
     * @return A CreateFunctionResult indicating the success or failure of the
     *         request.
     */
    private CreateFunctionResult createFunction(LambduhFunctionContext context) {
        CreateFunctionRequest createFunctionRequest = new CreateFunctionRequest();
        createFunctionRequest.setDescription(context.getDescription());
        createFunctionRequest.setRole(lambdaRoleArn);
        createFunctionRequest.setFunctionName(context.getFunctionName());
        createFunctionRequest.setHandler(context.getHandlerName());
        createFunctionRequest.setRuntime(context.getRuntime());
        createFunctionRequest.setTimeout(timeout);
        createFunctionRequest.setMemorySize(memorySize);
        createFunctionRequest.setVpcConfig(getVpcConfig());

        FunctionCode functionCode = new FunctionCode();
        functionCode.setS3Bucket(s3Bucket);
        functionCode.setS3Key(fileName);
        createFunctionRequest.setCode(functionCode);

        return lambdaClient.createFunction(createFunctionRequest);
    }

    /**
     * Creates VpcConfig in order that the lambda can be put within a Vpc
     *
     * @return VpcConfig
     */
    private VpcConfig getVpcConfig() {
        VpcConfig vpcConfig = new VpcConfig();
        vpcConfig.setSecurityGroupIds(vpcSecurityGroupsIds);
        vpcConfig.setSubnetIds(vpcSubnetIds);
        return vpcConfig;
    }

    /**
     * Makes an update function code call on behalf of the caller, deploying the
     * new function code to AWS lambda.
     *
     * @return An UpdateFunctionResult indicating the success or failure of the
     *         request.
     */
    private UpdateFunctionCodeResult updateFunctionCode(LambduhFunctionContext context) {
        UpdateFunctionCodeRequest updateFunctionRequest = new UpdateFunctionCodeRequest();
        updateFunctionRequest.setS3Bucket(s3Bucket);
        updateFunctionRequest.setS3Key(fileName);
        updateFunctionRequest.setFunctionName(context.getFunctionName());
        updateFunctionRequest.setPublish(Boolean.TRUE);

        return lambdaClient.updateFunctionCode(updateFunctionRequest);
    }

    /**
     * Makes an update function configuration call on behalf of the caller, setting the
     * new configuration for the given function.
     *
     * @return An UpdateFunctionConfigurationResult indicating the success or failure of the
     *         request.
     */
    private UpdateFunctionConfigurationResult updateFunctionConfig(LambduhFunctionContext context) {
        UpdateFunctionConfigurationRequest updateFunctionRequest = new UpdateFunctionConfigurationRequest();
        updateFunctionRequest.setDescription(description);
        updateFunctionRequest.setRole(lambdaRoleArn);
        updateFunctionRequest.setFunctionName(context.getFunctionName());
        updateFunctionRequest.setHandler(context.getHandlerName());
        updateFunctionRequest.setTimeout(timeout);
        updateFunctionRequest.setMemorySize(memorySize);
        updateFunctionRequest.setVpcConfig(getVpcConfig());

        return lambdaClient.updateFunctionConfiguration(updateFunctionRequest);
    }

    /**
     * Indicates if function configuration received from AWS Lambda differs
     * from the plugin configuration
     *
     * @param function function data received from a GetFunctionRequest call
     *
     * @return	true if function config. has changed, false otherwise
     */
    private boolean hasFunctionConfigChanged(GetFunctionResult function) {
        FunctionConfiguration config = function.getConfiguration();
        if (config == null)
            return false;

        return !config.getDescription().equals(description) ||
                !config.getHandler().equals(handler) ||
                !config.getRole().equals(lambdaRoleArn) ||
                config.getTimeout().intValue() != timeout ||
                config.getMemorySize().intValue() != memorySize;
    }

    /**
     * Attempts to update an existing function of the same name then deploys the
     * function code to AWS Lambda.
     */
    private void deployLambdaFunction(LambduhFunctionContext context) {
        try {
            // Get function, update if exists
            try {
                GetFunctionResult function = getFunction(context);
                if (function != null) {
                    // update config if changed
                    if (hasFunctionConfigChanged(function)) {
                        updateFunctionConfig(context);
                    }
                    
                    // update code
                    UpdateFunctionCodeResult result = updateFunctionCode(context);
                    getLog().info("Function updated and deployed: " + result.getFunctionArn());
                }
            } catch (ResourceNotFoundException notFound) {
                // create if function doesn't exist
                CreateFunctionResult result = createFunction(context);
                getLog().info("Function created and deployed: " + result.getFunctionArn());
            }
        } catch (Exception ex) {
            // error occurred
            getLog().error("Error getting / creating / updating function: ", ex);
        }
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
        getLog().debug(String.format("Local file's MD5 hash is %s.", localmd5));

        // See if the JAR is already current; if it is, let's not re-upload it.
        boolean remoteIsCurrent = false;
        try {
            ObjectMetadata currentObj = s3Client.getObjectMetadata(bucket, fileName);
            getLog().info(String.format("Object exists in S3 with MD5 hash %s.", currentObj.getETag()));

            // This comparison will no longer work if we ever go to multipart uploads.  Etags are not
            // computed as MD5 sums for multipart uploads in s3.
            remoteIsCurrent = localmd5.equals(currentObj.getETag());
        }
        catch (AmazonClientException ace) {
            getLog().info("Object does not exist in S3 or we cannot access it.");
        }

        if (remoteIsCurrent) {
            getLog().info("The package already in S3 is up-to-date, not uploading.");
        }
        else {
            getLog().info("Uploading " + functionCode + " to AWS S3 bucket "
                    + s3Bucket);
            s3Client.putObject(s3Bucket, fileName, file);
            getLog().info("Upload complete");
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
                s3Client.createBucket(s3Bucket, regionName);
                getLog().info("Created bucket " + s3Bucket);
                return s3Bucket;
            } catch (AmazonServiceException ase) {
                getLog().error(ase.getMessage());
                throw ase;
            } catch (AmazonClientException ace) {
                getLog().error(ace.getMessage());
                throw ace;
            }
        }
    }

    /**
     * Returns true if the s3 bucket indicated exists, false otherwise.
     * @return
     */
    private boolean bucketExists() {
        try {
            s3Client.headBucket(new HeadBucketRequest(s3Bucket));
            return true;
        }
        catch (AmazonServiceException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            else {
                // Some other error, but the bucket appears to exist.
                getLog().error(String.format("Got status code %d for bucket named '%s'", e.getStatusCode(), s3Bucket));
                return true;
            }
        }
    }
}
