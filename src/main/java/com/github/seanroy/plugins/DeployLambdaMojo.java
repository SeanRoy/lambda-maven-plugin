package com.github.seanroy.plugins;

import com.amazonaws.services.cloudwatchevents.model.ListRuleNamesByTargetRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleResult;
import com.amazonaws.services.cloudwatchevents.model.PutTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.Target;
import com.amazonaws.services.dynamodbv2.model.ListStreamsRequest;
import com.amazonaws.services.dynamodbv2.model.ListStreamsResult;
import com.amazonaws.services.dynamodbv2.model.Stream;
import com.amazonaws.services.lambda.model.*;
import com.amazonaws.services.s3.model.AmazonS3Exception;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.amazonaws.services.lambda.model.EventSourcePosition.LATEST;
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
                                              .andThen(createOrUpdateTriggers)
                                              .apply(lambdaFunction));
        } catch (ResourceNotFoundException ignored) {
            createFunction.andThen(createOrUpdateAliases)
                          .andThen(createOrUpdateTriggers)
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
                .withTimeout(lambdaFunction.getTimeout())
                .withMemorySize(lambdaFunction.getMemorySize())
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

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateSNSTopicSubscription = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getSNSTopic());
        CreateTopicRequest createTopicRequest = new CreateTopicRequest()
                .withName(trigger.getSNSTopic());
        CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);
        getLog().info("Topic " + createTopicResult.getTopicArn() + " created");

        SubscribeRequest subscribeRequest = new SubscribeRequest()
                .withTopicArn(createTopicResult.getTopicArn())
                .withEndpoint(lambdaFunction.getUnqualifiedFunctionArn())
                .withProtocol("lambda");
        SubscribeResult subscribeResult = snsClient.subscribe(subscribeRequest);
        getLog().info(lambdaFunction.getUnqualifiedFunctionArn() + " subscribed to " + createTopicResult.getTopicArn());
        getLog().info("Created " + trigger.getIntegration() + " trigger " + subscribeResult.getSubscriptionArn());

        AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                .withAction("lambda:InvokeFunction")
                .withPrincipal("sns.amazonaws.com")
                .withSourceArn(createTopicResult.getTopicArn())
                .withFunctionName(lambdaFunction.getFunctionName())
                .withStatementId(UUID.randomUUID().toString());
        AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
        getLog().debug("Added permission to lambda function " + addPermissionResult.toString());
        return trigger;
    };
    
    private BiFunction<Trigger, LambdaFunction, Trigger> addAlexaSkillsKitPermission = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getSNSTopic());

        AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
            .withAction("lambda:InvokeFunction")
            .withPrincipal("alexa-appkit.amazon.com")
            .withFunctionName(lambdaFunction.getFunctionName())
            .withStatementId(UUID.randomUUID().toString());
        
        AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
        getLog().debug("Added permission to lambda function " + addPermissionResult.toString());
        
        return trigger;
    };

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateScheduledRule = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getRuleName());
        ListRuleNamesByTargetRequest listRuleNamesByTargetRequest = new ListRuleNamesByTargetRequest()
                .withTargetArn(lambdaFunction.getUnqualifiedFunctionArn());
        boolean shouldCreateConfigurationForRule = eventsClient.listRuleNamesByTarget(listRuleNamesByTargetRequest).getRuleNames().stream().noneMatch(ruleName -> ruleName.equals(trigger.getRuleName()));

        if (shouldCreateConfigurationForRule) {
            PutRuleRequest putRuleRequest = new PutRuleRequest()
                    .withName(trigger.getRuleName())
                    .withDescription(trigger.getRuleDescription())
                    .withScheduleExpression(trigger.getScheduleExpression());
            PutRuleResult putRuleResult = eventsClient.putRule(putRuleRequest);
            getLog().info("Created " + trigger.getIntegration() + " trigger " + putRuleResult.getRuleArn());

            AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                    .withAction("lambda:InvokeFunction")
                    .withPrincipal("events.amazonaws.com")
                    .withSourceArn(putRuleResult.getRuleArn())
                    .withFunctionName(lambdaFunction.getFunctionName())
                    .withStatementId(UUID.randomUUID().toString());
            AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
            getLog().debug("Added permission to lambda function " + addPermissionResult.toString());

            PutTargetsRequest putTargetsRequest = new PutTargetsRequest()
                    .withRule(trigger.getRuleName())
                    .withTargets(new Target().withId("1").withArn(lambdaFunction.getUnqualifiedFunctionArn()));
            eventsClient.putTargets(putTargetsRequest);
        }
        return trigger;
    };

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateDynamoDBTrigger = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getDynamoDBTable());
        ListStreamsRequest listStreamsRequest = new ListStreamsRequest().withTableName(trigger.getDynamoDBTable());
        ListStreamsResult listStreamsResult = dynamoDBStreamsClient.listStreams(listStreamsRequest);

        String streamArn = listStreamsResult.getStreams().stream()
                                            .filter(s -> trigger.getDynamoDBTable().equals(s.getTableName()))
                                            .findFirst()
                                            .map(Stream::getStreamArn)
                                            .orElseThrow(() -> new IllegalArgumentException("Unable to find stream for table " + trigger.getDynamoDBTable()));

        ListEventSourceMappingsRequest listEventSourceMappingsRequest = new ListEventSourceMappingsRequest()
                .withFunctionName(lambdaFunction.getUnqualifiedFunctionArn());
        ListEventSourceMappingsResult listEventSourceMappingsResult = lambdaClient.listEventSourceMappings(listEventSourceMappingsRequest);

        Optional<EventSourceMappingConfiguration> eventSourceMappingConfiguration = listEventSourceMappingsResult.getEventSourceMappings().stream()
                                                                                                                 .filter(stream -> {
                                                                                                                     boolean isSameFunctionArn = stream.getFunctionArn().equals(lambdaFunction.getUnqualifiedFunctionArn());
                                                                                                                     boolean isSameSourceArn = stream.getEventSourceArn().equals(streamArn);
                                                                                                                     return isSameFunctionArn && isSameSourceArn;
                                                                                                                 })
                                                                                                                 .findFirst();

        if (eventSourceMappingConfiguration.isPresent()) {
            UpdateEventSourceMappingRequest updateEventSourceMappingRequest = new UpdateEventSourceMappingRequest()
                    .withUUID(eventSourceMappingConfiguration.get().getUUID())
                    .withFunctionName(lambdaFunction.getUnqualifiedFunctionArn())
                    .withBatchSize(ofNullable(trigger.getBatchSize()).orElse(10))
                    .withEnabled(ofNullable(trigger.getEnabled()).orElse(true));
            UpdateEventSourceMappingResult updateEventSourceMappingResult = lambdaClient.updateEventSourceMapping(updateEventSourceMappingRequest);
            trigger.withTriggerArn(updateEventSourceMappingResult.getEventSourceArn());
            getLog().info("Updated " + trigger.getIntegration() + " trigger " + trigger.getTriggerArn());
        } else {
            CreateEventSourceMappingRequest createEventSourceMappingRequest = new CreateEventSourceMappingRequest()
                    .withFunctionName(lambdaFunction.getUnqualifiedFunctionArn())
                    .withEventSourceArn(streamArn)
                    .withBatchSize(ofNullable(trigger.getBatchSize()).orElse(10))
                    .withStartingPosition(EventSourcePosition.fromValue(ofNullable(trigger.getStartingPosition()).orElse(LATEST.toString())))
                    .withEnabled(ofNullable(trigger.getEnabled()).orElse(true));
            CreateEventSourceMappingResult createEventSourceMappingResult = lambdaClient.createEventSourceMapping(createEventSourceMappingRequest);
            trigger.withTriggerArn(createEventSourceMappingResult.getEventSourceArn());
            getLog().info("Created " + trigger.getIntegration() + " trigger " + trigger.getTriggerArn());
        }
        return trigger;
    };

    private Function<LambdaFunction, LambdaFunction> createOrUpdateTriggers = (LambdaFunction lambdaFunction) -> {
        lambdaFunction.getTriggers().forEach(trigger -> {
            if ("CloudWatch Events - Schedule".equals(trigger.getIntegration())) {
                createOrUpdateScheduledRule.apply(trigger, lambdaFunction);
            } else if ("DynamoDB".equals(trigger.getIntegration())) {
                createOrUpdateDynamoDBTrigger.apply(trigger, lambdaFunction);
            } else if ("SNS".equals(trigger.getIntegration())) {
                createOrUpdateSNSTopicSubscription.apply(trigger, lambdaFunction);
            } else if ("Alexa Skills Kit".equals(trigger.getIntegration())) {
                addAlexaSkillsKitPermission.apply(trigger,  lambdaFunction);
            } else {
                throw new IllegalArgumentException("Unknown integration for trigger " + trigger.getIntegration() + ". Correct your configuration");
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
