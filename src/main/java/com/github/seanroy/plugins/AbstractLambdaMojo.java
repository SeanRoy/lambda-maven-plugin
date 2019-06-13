package com.github.seanroy.plugins;

import static com.amazonaws.util.CollectionUtils.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.s3.model.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
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
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.github.seanroy.utils.AWSEncryption;
import com.github.seanroy.utils.JsonUtil;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Abstracts all common parameter handling and initiation of AWS service clients.
 *
 * @author sean, <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 11/08/16.
 */
@SuppressWarnings("ClassWithTooManyFields")
public abstract class AbstractLambdaMojo extends AbstractMojo {
    public static final String TRIG_INT_LABEL_CLOUDWATCH_EVENTS = "CloudWatch Events - Schedule";
    public static final String TRIG_INT_LABEL_DYNAMO_DB = "DynamoDB";
    public static final String TRIG_INT_LABEL_KINESIS = "Kinesis";
    public static final String TRIG_INT_LABEL_SNS = "SNS";
    public static final String TRIG_INT_LABEL_ALEXA_SK = "Alexa Skills Kit";
    public static final String TRIG_INT_LABEL_LEX = "Lex";
    public static final String TRIG_INT_LABEL_SQS = "SQS";
    
    public static final String PERM_LAMBDA_INVOKE = "lambda:InvokeFunction";
    
    public static final String PRINCIPAL_ALEXA  = "alexa-appkit.amazon.com";
    public static final String PRINCIPAL_LEX    = "lex.amazonaws.com";
    public static final String PRINCIPAL_SNS    = "sns.amazonaws.com";
    public static final String PRINCIPAL_EVENTS = "events.amazonaws.com"; // Cloudwatch events
    public static final String PRINCIPAL_SQS    = "sqs.amazonaws.com";
    
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
    
    @Parameter(property = "alias")
    public String alias;
    
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
     * AWS S3 Server Side Encryption (SSE)
     * </p>
     */
    @Parameter(property = "sse", defaultValue = "false")
    public boolean sse;
    /**
     * <p>
     * AWS KMS Key ID for S3 Server Side Encryption (SSE)
     * </p>
     */
    @Parameter(property = "sseKmsEncryptionKeyArn")
    public String sseKmsEncryptionKeyArn;
    /**
     * <p>
     * S3 key prefix for the uploaded jar
     * </p>
     */
    @Parameter(property = "keyPrefix", defaultValue = "/")
    public String keyPrefix;
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
    @Parameter(property = "lambdaRoleArn", defaultValue = "${lambdaRoleArn}")
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
    
    @Parameter(property = "passThrough")
    public String passThrough;
    
    @Parameter(property = "kmsEncryptionKeyArn")
    public String kmsEncryptionKeyArn;
    
    @Parameter(property = "encryptedPassThrough")
    public String encryptedPassThrough;
    
    /**
     * Allows for proxy settings to passed to the lambda client.
     */
    @Parameter(property = "clientConfiguration")
    public Map<String, String> clientConfiguration;
    
    public String fileName;
    public AWSCredentials credentials;
    public AmazonS3 s3Client;
    public AWSLambda lambdaClient;
    public AmazonSNS snsClient;
    public AmazonCloudWatchEvents eventsClient;
    public AmazonDynamoDBStreams dynamoDBStreamsClient;
    public AmazonKinesis kinesisClient;
    public AmazonCloudWatchEvents cloudWatchEventsClient;
    public AmazonSQS sqsClient;

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

    void uploadJarToS3() throws Exception {
        String bucket = getBucket();
        File file = new File(functionCode);
        String localmd5 = DigestUtils.md5Hex(new FileInputStream(file));
        getLog().debug(String.format("Local file's MD5 hash is %s.", localmd5));

        ofNullable(getObjectMetadata(bucket))
                .map(ObjectMetadata::getETag)
                .map(remoteMD5 -> {
                    getLog().info(fileName + " exists in S3 with MD5 hash " + remoteMD5);
                    // This comparison will no longer work if we ever go to multipart uploads.  Etags are not
                    // computed as MD5 sums for multipart uploads in s3.
                    return localmd5.equals(remoteMD5);
                })
                .map(isTheSame -> {
                    if (isTheSame) {
                        getLog().info(fileName + " is up to date in AWS S3 bucket " + s3Bucket + ". Not uploading...");
                        return true;
                    }
                    return null; // file should be imported
                })
                .orElseGet(() -> {
                    upload(file);
                    return true;
                });
    }

    Function<LambdaFunction, LambdaFunction> updateFunctionCode = (LambdaFunction lambdaFunction) -> {
        getLog().info("About to update functionCode for " + lambdaFunction.getFunctionName());
        UpdateFunctionCodeRequest updateFunctionRequest = new UpdateFunctionCodeRequest()
                .withFunctionName(lambdaFunction.getFunctionName())
                .withS3Bucket(s3Bucket)
                .withS3Key(fileName)
                .withPublish(lambdaFunction.isPublish());
        UpdateFunctionCodeResult updateFunctionCodeResult = lambdaClient.updateFunctionCode(updateFunctionRequest);
        return lambdaFunction
                .withVersion(updateFunctionCodeResult.getVersion())
                .withFunctionArn(updateFunctionCodeResult.getFunctionArn());
    };

    private ObjectMetadata getObjectMetadata(String bucket) {
        try {
            return s3Client.getObjectMetadata(bucket, fileName);
        } catch (AmazonS3Exception ignored) {
            return null;
        }
    }

    private String getBucket() {
        if (s3Client.listBuckets().stream().noneMatch(p -> Objects.equals(p.getName(), s3Bucket))) {
            getLog().info("Created bucket s3://" + s3Client.createBucket(s3Bucket).getName());
        }
        return s3Bucket;
    }

    private PutObjectResult upload(File file) {
        getLog().info("Uploading " + functionCode + " to AWS S3 bucket " + s3Bucket);
        PutObjectRequest putObjectRequest = new PutObjectRequest(s3Bucket, fileName, file);
        if (sse) {
            if (sseKmsEncryptionKeyArn != null && sseKmsEncryptionKeyArn.length() > 0) {
                putObjectRequest.setSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams(sseKmsEncryptionKeyArn));
            } else {
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
                putObjectRequest.setMetadata(objectMetadata);
            }
        }
        PutObjectResult putObjectResult = s3Client.putObject(putObjectRequest);
        getLog().info("Upload complete...");
        return putObjectResult;
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
        if (!ofNullable(keyPrefix).orElse("/").endsWith("/")) {
            keyPrefix += "/";
        }
        fileName = keyPrefix + pieces[pieces.length - 1];
    }

    private void initVersion() {
        version = version.replace(".", "-");
    }
    
    @SuppressWarnings("rawtypes")
    BiFunction<AwsClientBuilder, ClientConfiguration, AmazonWebServiceClient> clientFactory = (builder, clientConfig) -> {
        Regions region = Regions.fromName(regionName);
        
        return (AmazonWebServiceClient) of(credentials)
        .map(credentials -> builder.withCredentials(new AWSStaticCredentialsProvider(credentials))
                                   .withClientConfiguration(clientConfig)
                                   .withRegion(region).build())
        .orElse(builder.withRegion(region).withCredentials(new DefaultAWSCredentialsProviderChain()).build());
    };

    private void initAWSClients() {
        ClientConfiguration clientConfig = clientConfiguration();
        s3Client = (AmazonS3) clientFactory.apply(AmazonS3ClientBuilder.standard(), clientConfig);
        lambdaClient = (AWSLambda) clientFactory.apply(AWSLambdaClientBuilder.standard(), clientConfig);
        snsClient = (AmazonSNS) clientFactory.apply(AmazonSNSClientBuilder.standard(), clientConfig);
        eventsClient = (AmazonCloudWatchEvents) clientFactory.apply(AmazonCloudWatchEventsClientBuilder.standard(), clientConfig);
        dynamoDBStreamsClient = (AmazonDynamoDBStreams) clientFactory.apply(AmazonDynamoDBStreamsClientBuilder.standard(), clientConfig);
        kinesisClient = (AmazonKinesis) clientFactory.apply(AmazonKinesisClientBuilder.standard(), clientConfig);
        cloudWatchEventsClient = (AmazonCloudWatchEvents) clientFactory.apply(AmazonCloudWatchEventsClientBuilder.standard(), clientConfig);
        sqsClient = (AmazonSQS) clientFactory.apply(AmazonSQSClientBuilder.standard(), clientConfig);
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
                          .withLambdaRoleArn(ofNullable(lambdaFunction.getLambdaRoleArn()).orElse(lambdaRoleArn))
                          .withAliases(aliases(lambdaFunction.isPublish()))
                          .withTriggers(ofNullable(lambdaFunction.getTriggers()).map(triggers -> triggers.stream()
                                                                                                         .map(trigger -> {
                                                                                                             trigger.withRuleName(addSuffix(trigger.getRuleName()));
                                                                                                             trigger.withSNSTopic(addSuffix(trigger.getSNSTopic()));
                                                                                                             trigger.withDynamoDBTable(addSuffix(trigger.getDynamoDBTable()));
                                                                                                             trigger.withLexBotName(addSuffix(trigger.getLexBotName()));
                                                                                                             trigger.withStandardQueue(addSuffix(trigger.getStandardQueue()));
                                                                                                             return trigger;
                                                                                                         })
                                                                                                         .collect(toList()))
                                                                                .orElse(new ArrayList<>()))
                          .withEnvironmentVariables(environmentVariables(lambdaFunction));                          

            return lambdaFunction;
        }).collect(toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> environmentVariables(LambdaFunction lambdaFunction) {
        // Get existing environment variables to interleave them with the new ones or replacements.
        Map<String, String> awsDefinedEnvVars = new HashMap<String, String>();
        
        try {
            awsDefinedEnvVars = ofNullable(lambdaClient.getFunctionConfiguration(new GetFunctionConfigurationRequest()
                .withFunctionName(lambdaFunction.getFunctionName())
                .withQualifier(lambdaFunction.getQualifier())).getEnvironment()).flatMap(x -> {
                    return of(x.getVariables());}).orElse(new HashMap<>());
        } catch(ResourceNotFoundException rnfe) {
            getLog().debug("Lambda function doesn't exist yet, no existing environment variables retrieved.");
        } catch(Exception e) {
            getLog().error("Could not retrieve existing environment variables " + e.getMessage());
        }
        
        Map<String, String> configurationEnvVars = ofNullable(environmentVariables).orElse(new HashMap<>());
        Map<String, String> functionEnvVars = ofNullable(lambdaFunction.getEnvironmentVariables()).orElse(new HashMap<>());
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        
        Map<String, String> passThroughEnvVars = 
            new GsonBuilder().create().fromJson(ofNullable(passThrough).orElse("{}"), type);
        
        passThroughEnvVars.putAll(ofNullable(kmsEncryptionKeyArn).flatMap(arn -> {
           AWSEncryption awsEncryptor = new AWSEncryption(arn);
           Map<String, String> encryptedVariables = 
                   new GsonBuilder().create().fromJson(ofNullable(encryptedPassThrough).orElse("{}"), type);
           encryptedVariables.replaceAll((k, v) -> {
               return awsEncryptor.encryptString(v);
           });
           return of(encryptedVariables);
        }).orElse(new HashMap<String,String>()));
        
        // There may be a smarter way of doing this, but we have a hierarchy of environment variables. Those at the top
        // may be overridden by variables below them.
        // 1. Variables defined manually in the AWS Lambda Console
        // 2. Variables defined at the Configuration Level of the pom.xml
        // 3. Variables defined at the Function Level within the Configuration Level of the pom.xml.
        // 4. Pass through variables defined at the command line.
        awsDefinedEnvVars.putAll(configurationEnvVars);
        awsDefinedEnvVars.putAll(functionEnvVars);
        awsDefinedEnvVars.putAll(passThroughEnvVars);
        
        return awsDefinedEnvVars;
    }
    
    private ClientConfiguration clientConfiguration() {
        return ofNullable(clientConfiguration).flatMap(clientConfigObject -> {    
                return of(new ClientConfiguration()
                    .withProtocol(Protocol.valueOf(
                            clientConfigObject.getOrDefault("protocol", Protocol.HTTPS.toString()).toUpperCase()))
                    .withProxyHost(clientConfigObject.get("proxyHost"))
                    .withProxyPort(Integer.getInteger(clientConfigObject.get("proxyPort"), -1))
                    .withProxyDomain(clientConfigObject.get("proxyDomain"))
                    .withProxyUsername(clientConfigObject.get("proxyUsername"))
                    .withProxyPassword(clientConfigObject.get("proxyPassword"))
                    .withProxyWorkstation(clientConfigObject.get("proxyWorkstation")));
        }).orElse(new ClientConfiguration());
    }

    private String addSuffix(String functionName) {
        return ofNullable(functionNameSuffix).map(suffix -> Stream.of(functionName, suffix).collect(Collectors.joining()))
                                             .orElse(functionName);
    }

    private List<String> aliases(boolean publish) {
        if (publish) {
            return new ArrayList<String>() {{ add(version); ofNullable(alias).ifPresent(a -> add(a)); }};
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
