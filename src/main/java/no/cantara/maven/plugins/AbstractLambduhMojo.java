package no.cantara.maven.plugins;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.s3.AmazonS3Client;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.amazonaws.util.CollectionUtils.isNullOrEmpty;
import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;


/**
 * Abstracts all common parameter handling and initiation of AWS service
 * clients.
 *
 * @author sean
 */
public abstract class AbstractLambduhMojo extends AbstractMojo {
    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    public String accessKey;
    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    public String secretKey;
    @Parameter(property = "functionCode", defaultValue = "${functionCode}", required = true)
    public String functionCode;
    @Parameter(property = "alias", defaultValue = "${alias}", required = true)
    public Alias alias;
    @Parameter(property = "version", defaultValue = "${version}", required = true)
    public String version;
    @Parameter(property = "region", alias="region", defaultValue = "eu-west-1")
    public String regionName;
    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    public String s3Bucket;
    @Parameter(property = "runtime", defaultValue = "java8")
    public String runtime;
    @Parameter(property = "lambdaRoleArn", defaultValue = "${lambdaRoleArn}", required = true)
    public String lambdaRoleArn;
    @Parameter(property = "lambdaFunctionsJSON", defaultValue = "", required = false)
    public String lambdaFunctionsJSON;
    @Parameter(property = "lambdaFunctions", defaultValue = "${lambdaFunctions}", required = false)
    public List<LambdaFunction> lambdaFunctions;
    @Parameter(property = "timeout", defaultValue = "30")
    public int timeout;
    @Parameter(property = "memorySize", defaultValue = "1024")
    public int memorySize;
    @Parameter(property = "environmentVpcSecurityGroupsIds", defaultValue = "${environmentVpcSecurityGroupsIds}")
    public Map<String, String> environmentVpcSecurityGroupsIds;
    @Parameter(property = "environmentVpcSubnetIds", defaultValue = "${environmentVpcSubnetIds}")
    public Map<String, String> environmentVpcSubnetIds;

    public String fileName;
    public AWSCredentials credentials;
    public AmazonS3Client s3Client;
    public AWSLambdaClient lambdaClient;
    public final Map<Alias, List<String>> vpcSecurityGroup = new HashMap<>();
    public final Map<Alias, List<String>> vpcSubnetIds = new HashMap<>();

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Alias: " + alias.toString());
        initAWSCredentials();
        initAWSClients();
        try {
            initFileName();
            initVersion();
            initVpc();
            initLambdaFunctionsConfiguration();

            getLog().debug("FunctionCode: " + functionCode);
            getLog().debug("Version: " + version);
            getLog().debug("Got config for functions:");
            lambdaFunctions.stream().forEach(lambdaFunction -> getLog().debug(lambdaFunction.toString()));
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

    private void initAWSClients() {
        s3Client = of(credentials).map(AmazonS3Client::new)
                                  .orElse(new AmazonS3Client());
        lambdaClient = of(credentials).map(credentials -> new AWSLambdaClient().<AWSLambdaClient>withRegion(Regions.fromName(regionName)))
                                      .orElse(new AWSLambdaClient().withRegion(Regions.fromName(regionName)));
    }

    private void initLambdaFunctionsConfiguration() throws MojoExecutionException, IOException {
        if (lambdaFunctionsJSON != null) {
            this.lambdaFunctions = JsonUtil.fromJson(lambdaFunctionsJSON);
        }
        validate(lambdaFunctions);

        lambdaFunctions = lambdaFunctions.stream().map(lambdaFunction -> {
            lambdaFunction.withFunctionName(ofNullable(lambdaFunction.getFunctionName()).orElseThrow(() -> new IllegalArgumentException("Configuration error. LambdaFunction -> 'functionName' is required")))
                          .withHandler(ofNullable(lambdaFunction.getHandler()).orElseThrow(() -> new IllegalArgumentException("Configuration error. LambdaFunction -> 'handler' is required")))
                          .withDescription(ofNullable(lambdaFunction.getDescription()).orElse(""))
                          .withTimeout(ofNullable(lambdaFunction.getTimeout()).orElse(timeout))
                          .withMemorySize(ofNullable(lambdaFunction.getMemorySize()).orElse(memorySize))
                          .withSubnetIds(ofNullable(initSubnetIds(lambdaFunction)).orElse(new ArrayList<>()))
                          .withSecurityGroupsIds(ofNullable(initSecurityGroupsIds(lambdaFunction)).orElse(new ArrayList<>()))
                          .withVersion(version)
                          .withAliases(aliases());
            return lambdaFunction;
        }).collect(toList());
    }

    private List<String> aliases() {
        switch (alias) {
            case DEV:
                return asList(alias.toString(), "LATEST");
            case TEST:
            case PROD:
                return asList(alias.toString(), version);
            default:
                throw new IllegalArgumentException("Unable to generate aliases");
        }
    }

    private List<String> initSecurityGroupsIds(LambdaFunction f) {
        if (!isNullOrEmpty(f.getSecurityGroupsIds())) {
            return f.getSecurityGroupsIds();
        }
        return vpcSecurityGroup.getOrDefault(alias, new ArrayList<>());
    }

    private List<String> initSubnetIds(LambdaFunction f) {
        if (!isNullOrEmpty(f.getSubnetIds())) {
            return f.getSubnetIds();
        }
        return vpcSubnetIds.getOrDefault(alias, new ArrayList<>());
    }

    private void initVpc() {
        ofNullable(environmentVpcSecurityGroupsIds).ifPresent(m -> m.entrySet().stream()
                                                                    .forEach(entry -> vpcSecurityGroup.put(Alias.valueOf(entry.getKey()), splitToList(entry.getValue()))));

        ofNullable(environmentVpcSubnetIds).ifPresent(m -> m.entrySet().stream()
                                                            .forEach(entry -> vpcSubnetIds.put(Alias.valueOf(entry.getKey()), splitToList(entry.getValue()))));
    }

    private List<String> splitToList(String value) {
        return ofNullable(value).map(s -> asList(s.split(","))).orElse(new ArrayList<>());
    }

    private void validate(List<LambdaFunction> lambdaFunctions) throws MojoExecutionException {
        if (isNullOrEmpty(lambdaFunctions)) {
            getLog().error("At least one function has to be provided in configuration");
            throw new MojoExecutionException("Illegal configuration. Configuration for at least one Lambda function has to be provided");
        }
    }
}
