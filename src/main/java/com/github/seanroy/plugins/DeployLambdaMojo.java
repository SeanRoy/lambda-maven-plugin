package com.github.seanroy.plugins;

import com.amazonaws.services.cloudwatchevents.model.ListRuleNamesByTargetRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleResult;
import com.amazonaws.services.cloudwatchevents.model.PutTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.Target;
import com.amazonaws.services.lambda.model.AddPermissionRequest;
import com.amazonaws.services.lambda.model.AddPermissionResult;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * I am a deploy mojo responsible to upload and create or update lambda function in AWS.
 *
 * @author Sean N. Roy, <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 11/08/16.
 */
@Mojo(name = "deploy-lambda")
public class DeployLambdaMojo extends AbstractLambdaMojo {

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            uploadJarToS3();
            lambdaFunctions.stream().map(f -> {
                getLog().info("---- Create or update " + f.getFunctionName() + " -----");
                return f;
            }).forEach(this::createOrUpdate);
        } catch (Exception e) {
            getLog().error("Error during processing", e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private Void createOrUpdate(LambdaFunction lambdaFunction) {
        try {
            of(getFunction(lambdaFunction))
                    .filter(getFunctionResult -> shouldUpdate(lambdaFunction, getFunctionResult))
                    .map(getFunctionResult ->
                            updateFunctionCode.andThen(updateFunctionConfig)
                                              .andThen(createOrUpdateAliases)
                                              .andThen(createOrUpdateSNSTopicSubscriptions)
                                              .andThen(createOrUpdateScheduledRules)
                                              .apply(lambdaFunction));
        } catch (ResourceNotFoundException ignored) {
            createFunction.andThen(createOrUpdateAliases)
                          .andThen(createOrUpdateSNSTopicSubscriptions)
                          .andThen(createOrUpdateScheduledRules)
                          .apply(lambdaFunction);
        }
        return null;
    }

    private boolean shouldUpdate(LambdaFunction lambdaFunction, GetFunctionResult getFunctionResult) {
        boolean isConfigurationChanged = isConfigurationChanged(lambdaFunction, getFunctionResult);
        if (!isConfigurationChanged) {
            getLog().info("Config hasn't changed for " + lambdaFunction.getFunctionName());
        }
        if (forceUpdate) {
            getLog().info("Forcing update for " + lambdaFunction.getFunctionName());
        }

        return forceUpdate || isConfigurationChanged;
    }

    private Function<LambdaFunction, LambdaFunction> updateFunctionCode = (LambdaFunction lambdaFunction) -> {
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

    private Function<LambdaFunction, LambdaFunction> updateFunctionConfig = (LambdaFunction lambdaFunction) -> {
        getLog().info("About to update functionConfig for " + lambdaFunction.getFunctionName());
        UpdateFunctionConfigurationRequest updateFunctionRequest = new UpdateFunctionConfigurationRequest()
                .withFunctionName(lambdaFunction.getFunctionName())
                .withDescription(lambdaFunction.getDescription())
                .withHandler(lambdaFunction.getHandler())
                .withRole(lambdaRoleArn)
                .withTimeout(timeout)
                .withMemorySize(memorySize)
                .withRuntime(runtime)
                .withVpcConfig(getVpcConfig(lambdaFunction));
        lambdaClient.updateFunctionConfiguration(updateFunctionRequest);
        return lambdaFunction;
    };

    private Function<LambdaFunction, LambdaFunction> createOrUpdateAliases = (LambdaFunction lambdaFunction) -> {
        lambdaFunction.getAliases().forEach(alias -> {
            UpdateAliasRequest updateAliasRequest = new UpdateAliasRequest()
                    .withFunctionName(lambdaFunction.getFunctionName())
                    .withFunctionVersion(lambdaFunction.getVersion())
                    .withName(alias);
            try {
                lambdaClient.updateAlias(updateAliasRequest);
                getLog().info("Alias " + alias + " updated for " + lambdaFunction.getFunctionName() + " with version " + lambdaFunction.getVersion());
            } catch (ResourceNotFoundException ignored) {
                CreateAliasRequest createAliasRequest = new CreateAliasRequest()
                        .withFunctionName(lambdaFunction.getFunctionName())
                        .withFunctionVersion(lambdaFunction.getVersion())
                        .withName(alias);
                lambdaClient.createAlias(createAliasRequest);
                getLog().info("Alias " + alias + " created for " + lambdaFunction.getFunctionName() + " with version " + lambdaFunction.getVersion());
            }
        });
        return lambdaFunction;
    };

    private Function<LambdaFunction, LambdaFunction> createOrUpdateSNSTopicSubscriptions = (LambdaFunction lambdaFunction) -> {
        lambdaFunction.getTopics().forEach(topic -> {
            CreateTopicRequest createTopicRequest = new CreateTopicRequest()
                    .withName(topic);
            CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);
            getLog().info("Topic " + createTopicResult.getTopicArn() + " created");

            SubscribeRequest subscribeRequest = new SubscribeRequest()
                    .withTopicArn(createTopicResult.getTopicArn())
                    .withEndpoint(lambdaFunction.getFunctionArn())
                    .withProtocol("lambda");
            SubscribeResult subscribeResult = snsClient.subscribe(subscribeRequest);
            getLog().info(lambdaFunction.getFunctionArn() + " subscribed to " + createTopicResult.getTopicArn());
            getLog().debug("Created subscription " + subscribeResult.getSubscriptionArn());

            AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                    .withAction("lambda:InvokeFunction")
                    .withPrincipal("sns.amazonaws.com")
                    .withSourceArn(createTopicResult.getTopicArn())
                    .withFunctionName(lambdaFunction.getFunctionName())
                    .withStatementId(UUID.randomUUID().toString());
            AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
            getLog().debug("Added permission to lambda function " + addPermissionResult.toString());
        });
        return lambdaFunction;
    };

    private Function<LambdaFunction, LambdaFunction> createOrUpdateScheduledRules = (LambdaFunction lambdaFunction) -> {
        lambdaFunction.getScheduledRules().forEach(rule -> {
            List<String> ruleNames = eventsClient.listRuleNamesByTarget(new ListRuleNamesByTargetRequest().withTargetArn(lambdaFunction.getFunctionArn())).getRuleNames();
            if (!ruleNames.contains(rule.getName())) {
                PutRuleRequest putRuleRequest = new PutRuleRequest()
                        .withName(rule.getName())
                        .withDescription(rule.getDescription())
                        .withScheduleExpression(rule.getScheduleExpression());
                PutRuleResult putRuleResult = eventsClient.putRule(putRuleRequest);
                getLog().info("Created rule " + putRuleResult.getRuleArn());

                AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                        .withAction("lambda:InvokeFunction")
                        .withPrincipal("events.amazonaws.com")
                        .withSourceArn(putRuleResult.getRuleArn())
                        .withFunctionName(lambdaFunction.getFunctionName())
                        .withStatementId(UUID.randomUUID().toString());
                AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
                getLog().debug("Added permission to lambda function " + addPermissionResult.toString());

                PutTargetsRequest putTargetsRequest = new PutTargetsRequest()
                        .withRule(rule.getName())
                        .withTargets(new Target().withId("1").withArn(lambdaFunction.getFunctionArn()));
                eventsClient.putTargets(putTargetsRequest);
            }
        });
        return lambdaFunction;
    };

    private GetFunctionResult getFunction(LambdaFunction lambdaFunction) {
        return lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(lambdaFunction.getFunctionName()));
    }

    private boolean isConfigurationChanged(LambdaFunction lambdaFunction, GetFunctionResult function) {
        BiPredicate<String, String> isChangeStr = (s0, s1) -> !Objects.equals(s0, s1);
        BiPredicate<Integer, Integer> isChangeInt = (i0, i1) -> !Objects.equals(i0, i1);
        BiPredicate<List<String>, List<String>> isChangeList = (l0, l1) -> !(l0.containsAll(l1) && l1.containsAll(l0));
        return of(function.getConfiguration())
                .map(config -> {
                    boolean isDescriptionChanged = isChangeStr.test(config.getDescription(), lambdaFunction.getDescription());
                    boolean isHandlerChanged = isChangeStr.test(config.getHandler(), lambdaFunction.getHandler());
                    boolean isRoleChanged = isChangeStr.test(config.getRole(), lambdaRoleArn);
                    boolean isTimeoutChanged = isChangeInt.test(config.getTimeout(), lambdaFunction.getTimeout());
                    boolean isMemoryChanged = isChangeInt.test(config.getMemorySize(), lambdaFunction.getMemorySize());
                    boolean isSecurityGroupIdsChanged = isChangeList.test(config.getVpcConfig().getSecurityGroupIds(), lambdaFunction.getSecurityGroupIds());
                    boolean isVpcSubnetIdsChanged = isChangeList.test(config.getVpcConfig().getSubnetIds(), lambdaFunction.getSubnetIds());
                    return isDescriptionChanged || isHandlerChanged || isRoleChanged || isTimeoutChanged || isMemoryChanged || isSecurityGroupIdsChanged || isVpcSubnetIdsChanged || isAliasesChanged(lambdaFunction);
                })
                .orElse(true);
    }

    private boolean isAliasesChanged(LambdaFunction lambdaFunction) {
        try {
            ListAliasesResult listAliasesResult = lambdaClient.listAliases(new ListAliasesRequest()
                    .withFunctionName(lambdaFunction.getFunctionName()));
            List<String> configuredAliases = listAliasesResult.getAliases().stream()
                                                              .map(AliasConfiguration::getName)
                                                              .collect(toList());
            return !configuredAliases.containsAll(lambdaFunction.getAliases());
        } catch (ResourceNotFoundException ignored) {
            return true;
        }
    }

    private Function<LambdaFunction, LambdaFunction> createFunction = (LambdaFunction lambdaFunction) -> {
        getLog().info("About to create function " + lambdaFunction.getFunctionName());
        CreateFunctionRequest createFunctionRequest = new CreateFunctionRequest()
                .withDescription(lambdaFunction.getDescription())
                .withRole(lambdaRoleArn)
                .withFunctionName(lambdaFunction.getFunctionName())
                .withHandler(lambdaFunction.getHandler())
                .withRuntime(runtime)
                .withTimeout(ofNullable(lambdaFunction.getTimeout()).orElse(timeout))
                .withMemorySize(ofNullable(lambdaFunction.getMemorySize()).orElse(memorySize))
                .withVpcConfig(getVpcConfig(lambdaFunction))
                .withCode(new FunctionCode()
                        .withS3Bucket(s3Bucket)
                        .withS3Key(fileName));
        CreateFunctionResult createFunctionResult = lambdaClient.createFunction(createFunctionRequest);
        lambdaFunction.withVersion(createFunctionResult.getVersion())
                      .withFunctionArn(createFunctionResult.getFunctionArn());
        getLog().info("Function " + createFunctionResult.getFunctionName() + " created. Function Arn: " + createFunctionResult.getFunctionArn());
        return lambdaFunction;
    };

    private VpcConfig getVpcConfig(LambdaFunction lambdaFunction) {
        return new VpcConfig()
                .withSecurityGroupIds(lambdaFunction.getSecurityGroupIds())
                .withSubnetIds(lambdaFunction.getSubnetIds());
    }

    private void uploadJarToS3() throws Exception {
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

    private PutObjectResult upload(File file) {
        getLog().info("Uploading " + functionCode + " to AWS S3 bucket " + s3Bucket);
        PutObjectResult putObjectResult = s3Client.putObject(s3Bucket, fileName, file);
        getLog().info("Upload complete...");
        return putObjectResult;
    }

    private ObjectMetadata getObjectMetadata(String bucket) {
        try {
            return s3Client.getObjectMetadata(bucket, fileName);
        } catch (AmazonS3Exception ignored) {
            return null;
        }
    }

    private String getBucket() {
        if (s3Client.listBuckets().stream().noneMatch(p -> p.getName().equals(s3Bucket))) {
            getLog().info("Created bucket s3://" + s3Client.createBucket(s3Bucket).getName());
        }
        return s3Bucket;
    }
}
