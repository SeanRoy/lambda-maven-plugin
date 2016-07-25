package com.github.seanroy.plugins;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * Abstracts all common parameter handling and initiation of AWS service
 * clients.
 * 
 * @author sean
 *
 */
public abstract class AbstractLambduhMojo extends AbstractMojo {
    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    protected String accessKey;

    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    protected String secretKey;

    @Parameter(required = true, defaultValue = "${functionCode}")
    protected String functionCode;

    @Parameter(alias = "region", property = "region", defaultValue = "us-east-1")
    protected String regionName;

    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    protected String s3Bucket;

    @Parameter(property = "description", defaultValue = "")
    protected String description;

    @Parameter(required = true, defaultValue = "${lambdaRoleArn}")
    protected String lambdaRoleArn;

    @Parameter(property = "functionName", defaultValue = "${functionName}")
    protected String functionName;

    @Parameter(required = true, defaultValue = "${handler}")
    protected String handler;

    @Parameter(property = "runtime", defaultValue = "Java8")
    protected Runtime runtime;

    /**
     * Lambda function execution timeout. Defaults to maximum allowed.
     */
    @Parameter(property = "timeout", defaultValue = "60")
    protected int timeout;

    @Parameter(property = "memorySize", defaultValue = "128")
    protected int memorySize;

    @Parameter(property = "vpcSecurityGroupsIds")
    protected List<String> vpcSecurityGroupsIds;

    @Parameter(property = "vpcSubnetIds")
    protected List<String> vpcSubnetIds;

    protected String fileName;
    protected Region region;

    protected AWSCredentials credentials;
    protected AmazonS3Client s3Client;
    protected AWSLambdaClient lambdaClient;
    
    public void execute() throws MojoExecutionException {
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
        
        String pattern = Pattern.quote(File.separator);
        String[] pieces = functionCode.split(pattern);
        fileName = pieces[pieces.length - 1];

    }
}
