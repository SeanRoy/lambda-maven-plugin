package com.github.seanroy.plugins;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEvents;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEventsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amazonaws.util.CollectionUtils.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Abstracts all common parameter handling and initiation of AWS service clients.
 *
 * @author sean, <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 11/08/16.
 */
@SuppressWarnings("ClassWithTooManyFields")
public abstract class AbstractLambdaMojo extends AbstractMojo {
    /**
     * <p>The AWS access key.</p>
     */
    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    public String accessKey;
    /**
     * <p>The AWS secret access key.</p>
     */
    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    public String secretKey;
    /**
     * <p>The path to deliverable.</p>
     */
    @Parameter(property = "functionCode", defaultValue = "${functionCode}", required = true)
    public String functionCode;
    /**
     * <p>The version of deliverable. Example value can be 1.0-SNAPSHOT.</p>
     */
    @Parameter(property = "version", defaultValue = "${version}", required = true)
    public String version;
    /**
     * <p>Amazon region. Default value is us-east-1.</p>
     */
    @Parameter(property = "region", alias = "region", defaultValue = "us-east-1")
    public String regionName;
    /**
     * <p>
     * Amazon S3 bucket name where the .zip file containing your deployment
     * package is stored. This bucket must reside in the same AWS region where
     * you are creating the Lambda function.
     * </p>
     */
    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    public String s3Bucket;
    /**
     * <p>
     * The runtime environment for the Lambda function.
     * </p>
     * <p>
     * To use the Node.js runtime v4.3, set the value to "nodejs4.3". To use
     * earlier runtime (v0.10.42), set the value to "nodejs".
     * </p>
     */
    @Parameter(property = "runtime", defaultValue = "java8")
    public String runtime;
    /**
     * <p>The Amazon Resource Name (ARN) of the IAM role that Lambda will assume when it executes your function.</p>
     */
    @Parameter(property = "lambdaRoleArn", defaultValue = "${lambdaRoleArn}", required = true)
    public String lambdaRoleArn;
    /**
     * <p>The JSON confuguration for Lambda functions. @see {@link LambdaFunction}.</p>
     */
    @Parameter(property = "lambdaFunctionsJSON")
    public String lambdaFunctionsJSON;
    /**
     * <p>The confuguration for Lambda functions. @see {@link LambdaFunction}. Can be configured in pom.xml. Automaticall parsed from JSON configuration.</p>
     */
    @Parameter(property = "lambdaFunctions", defaultValue = "${lambdaFunctions}")
    public List<LambdaFunction> lambdaFunctions;
    /**
     * <p>
     * The function execution time at which AWS Lambda should terminate the
     * function. Because the execution time has cost implications, we recommend
     * you set this value based on your expected execution time. The default is 30 seconds.
     * </p>
     */
    @Parameter(property = "timeout", defaultValue = "30")
    public int timeout;
    /**
     * <p>
     * The amount of memory, in MB, your Lambda function is given. AWS Lambda
     * uses this memory size to infer the amount of CPU allocated to your
     * function. Your function use-case determines your CPU and memory
     * requirements. For example, a database operation might need less memory
     * compared to an image processing function. The default value is 1024 MB.
     * The value must be a multiple of 64 MB.
     * </p>
     */
    @Parameter(property = "memorySize", defaultValue = "1024")
    public int memorySize;
    /**
     * <p>A list of one or more security groups IDs in your VPC.</p>
     */
    @Parameter(property = "vpcSecurityGroupIds", defaultValue = "${vpcSecurityGroupIds}")
    public List<String> vpcSecurityGroupIds;
    /**
     * <p>A list of one or more subnet IDs in your VPC.</p>
     */
    @Parameter(property = "vpcSubnetIds", defaultValue = "${vpcSubnetIds}")
    public List<String> vpcSubnetIds;
    /**
     * <p>This boolean parameter can be used to request AWS Lambda to update the
     * Lambda function and publish a version as an atomic operation.</p>
     */
    @Parameter(property = "publish", defaultValue = "true")
    public boolean publish;
    /**
     * <p>The suffix for the lambda function.</p>
     */
    @Parameter(property = "functionNameSuffix")
    public String functionNameSuffix;
    /**
     * <p>This boolean parameter can be used to force update of existing configuration. Use it when you don't publish a function and want to replece code in your Lambda function.</p>
     */
    @Parameter(property = "forceUpdate", defaultValue = "false")
    public boolean forceUpdate;
    /**
     * <p>This map parameter can be used to define environment variables for Lambda functions enable you to dynamically pass settings to your function code and libraries, without making changes to your code. Deployment functionality merges those variables with the one provided in json configuration.</p>
     */
    @Parameter(property = "environmentVariables", defaultValue = "${environmentVariables}")
    public Map<String, String> environmentVariables;

    public String fileName;
    public AWSCredentials credentials;
    public AmazonS3 s3Client;
    public AWSLambda lambdaClient;
    public AmazonSNS snsClient;
    public AmazonCloudWatchEvents eventsClient;
    public AmazonDynamoDBStreams dynamoDBStreamsClient;
    public AmazonKinesis kinesisClient;

    @Override
    public void execute() throws MojoExecutionException {
        initAWSCredentials();
        initAWSClients();
        try {
            initFileName();
            initVersion();
            initLambdaFunctionsConfiguration();

            lambdaFunctions.forEach(lambdaFunction -> getLog().debug(lambdaFunction.toString()));
        } catch (Exception e) {
            getLog().error("Initialization of configuration failed", e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private void initAWSCredentials() throws MojoExecutionException {
        DefaultAWSCredentialsProviderChain defaultChain = new DefaultAWSCredentialsProviderChain();
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        } else if (defaultChain.getCredentials() != null) {
            credentials = defaultChain.getCredentials();
        }

        if (credentials == null) {
            getLog().error("Unable to initialize AWS Credentials. Set BasicAWSCredentials with accessKey and secretKey or configure DefaultAWSCredentialsProviderChain");
            throw new MojoExecutionException("AWS Credentials config error");
        }
    }

    private void initFileName() {
        String pattern = Pattern.quote(File.separator);
        String[] pieces = functionCode.split(pattern);
        fileName = pieces[pieces.length - 1];
    }

    private void initVersion() {
        version = version.replace(".", "-");
    }
    
    @SuppressWarnings("rawtypes")
    Function<AwsClientBuilder, AmazonWebServiceClient> clientFactory = b -> {
        Regions region = Regions.fromName(regionName);
        
        return (AmazonWebServiceClient) of(credentials)
        .map(credentials -> b.withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(region).build())
        .orElse(b.withRegion(region).withCredentials(new DefaultAWSCredentialsProviderChain()).build());
    };

    private void initAWSClients() {
        s3Client = (AmazonS3) clientFactory.apply(AmazonS3ClientBuilder.standard());
        lambdaClient = (AWSLambda) clientFactory.apply(AWSLambdaClientBuilder.standard());
        snsClient = (AmazonSNS) clientFactory.apply(AmazonSNSClientBuilder.standard());
        eventsClient = (AmazonCloudWatchEvents) clientFactory.apply(AmazonCloudWatchEventsClientBuilder.standard());
        dynamoDBStreamsClient = (AmazonDynamoDBStreams) clientFactory.apply(AmazonDynamoDBStreamsClientBuilder.standard());
        kinesisClient = (AmazonKinesis) clientFactory.apply(AmazonKinesisClientBuilder.standard());
    }

    private void initLambdaFunctionsConfiguration() throws MojoExecutionException, IOException {
        if (lambdaFunctionsJSON != null) {
            this.lambdaFunctions = JsonUtil.fromJson(lambdaFunctionsJSON);
        }
        validate(lambdaFunctions);

        lambdaFunctions = lambdaFunctions.stream().map(lambdaFunction -> {
            String functionName = ofNullable(lambdaFunction.getFunctionName()).orElseThrow(() -> new IllegalArgumentException("Configuration error. LambdaFunction -> 'functionName' is required"));

            lambdaFunction.withFunctionName(addSuffix(functionName))
                          .withHandler(ofNullable(lambdaFunction.getHandler()).orElseThrow(() -> new IllegalArgumentException("Configuration error. LambdaFunction -> 'handler' is required")))
                          .withDescription(ofNullable(lambdaFunction.getDescription()).orElse(""))
                          .withTimeout(ofNullable(lambdaFunction.getTimeout()).orElse(timeout))
                          .withMemorySize(ofNullable(lambdaFunction.getMemorySize()).orElse(memorySize))
                          .withSubnetIds(ofNullable(vpcSubnetIds).orElse(new ArrayList<>()))
                          .withSecurityGroupsIds(ofNullable(vpcSecurityGroupIds).orElse(new ArrayList<>()))
                          .withVersion(version)
                          .withPublish(ofNullable(lambdaFunction.isPublish()).orElse(publish))
                          .withAliases(aliases(lambdaFunction.isPublish()))
                          .withTriggers(ofNullable(lambdaFunction.getTriggers()).map(triggers -> triggers.stream()
                                                                                                         .map(trigger -> {
                                                                                                             trigger.withRuleName(addSuffix(trigger.getRuleName()));
                                                                                                             trigger.withSNSTopic(addSuffix(trigger.getSNSTopic()));
                                                                                                             trigger.withDynamoDBTable(addSuffix(trigger.getDynamoDBTable()));
                                                                                                             return trigger;
                                                                                                         })
                                                                                                         .collect(toList()))
                                                                                .orElse(new ArrayList<>()))
                          .withEnvironmentVariables(environmentVariables(lambdaFunction));

            return lambdaFunction;
        }).collect(toList());
    }

    private Map<String, String> environmentVariables(LambdaFunction lambdaFunction) {
        Map<String, String> envVar0 = ofNullable(environmentVariables).orElse(new HashMap<>());
        Map<String, String> envVar1 = ofNullable(lambdaFunction.getEnvironmentVariables()).orElse(new HashMap<>());
        return Stream.of(envVar0, envVar1)
                     .map(Map::entrySet)
                     .flatMap(Collection::stream)
                     .collect(toMap(Entry::getKey, Entry::getValue));
    }

    private String addSuffix(String functionName) {
        return ofNullable(functionNameSuffix).map(suffix -> Stream.of(functionName, suffix).collect(Collectors.joining()))
                                             .orElse(functionName);
    }

    private List<String> aliases(boolean publish) {
        if (publish) {
            return Collections.singletonList(version);
        }
        return emptyList();
    }

    private void validate(List<LambdaFunction> lambdaFunctions) throws MojoExecutionException {
        if (isNullOrEmpty(lambdaFunctions)) {
            getLog().error("At least one function has to be provided in configuration");
            throw new MojoExecutionException("Illegal configuration. Configuration for at least one Lambda function has to be provided");
        }
    }
}
