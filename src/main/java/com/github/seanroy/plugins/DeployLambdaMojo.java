package com.github.seanroy.plugins;

import static com.amazonaws.services.lambda.model.EventSourcePosition.LATEST;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.services.cloudwatchevents.model.DeleteRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.DescribeRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.DescribeRuleResult;
import com.amazonaws.services.cloudwatchevents.model.ListRuleNamesByTargetRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleResult;
import com.amazonaws.services.cloudwatchevents.model.PutTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.RemoveTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.Target;
import com.amazonaws.services.dynamodbv2.model.ListStreamsRequest;
import com.amazonaws.services.dynamodbv2.model.ListStreamsResult;
import com.amazonaws.services.dynamodbv2.model.Stream;
import com.amazonaws.services.lambda.model.AddPermissionRequest;
import com.amazonaws.services.lambda.model.AddPermissionResult;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
import com.amazonaws.services.lambda.model.EventSourcePosition;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.GetPolicyRequest;
import com.amazonaws.services.lambda.model.GetPolicyResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsRequest;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.RemovePermissionRequest;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.UpdateEventSourceMappingResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.services.lambda.model.VpcConfigResponse;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;

/**
 * I am a deploy mojo responsible to upload and create or update lambda function in AWS.
 *
 * @author Sean N. Roy, <a href="mailto:sean.roy@gmail.com">Sean Roy</a> 11/08/16.
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
            }).forEach(lf ->
                getFunctionPolicy
                    .andThen(cleanUpOrphans)
                    .andThen(createOrUpdate)
                    .apply(lf));
        } catch (Exception e) {
            getLog().error("Error during processing", e);
            throw new MojoExecutionException(e.getMessage());
        }
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
    
    /*
     *  Get the existing policy function (on updates) and assign it to the lambdaFunction.
     */
    private Function<LambdaFunction, LambdaFunction> getFunctionPolicy = (LambdaFunction lambdaFunction) -> {
        try {
            lambdaFunction.setExistingPolicy(Policy.fromJson(lambdaClient.getPolicy(new GetPolicyRequest()
                .withFunctionName(lambdaFunction.getFunctionName())
                .withQualifier(lambdaFunction.getQualifier())).getPolicy()));
        } catch (ResourceNotFoundException rnfe3) {
            getLog().debug("Probably creating a new function, policy doesn't exist yet: " + rnfe3.getMessage());
        } 
        
        return lambdaFunction;
    };

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
                .withVpcConfig(getVpcConfig(lambdaFunction))
                .withEnvironment(new Environment().withVariables(lambdaFunction.getEnvironmentVariables()));
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
                lambdaClient.updateAlias(updateAliasRequest
                        );
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


        Optional<Statement> statementOpt;
        try {
            GetPolicyRequest getPolicyRequest = new GetPolicyRequest()
                    .withFunctionName(lambdaFunction.getFunctionName());
            GetPolicyResult GetPolicyResult = lambdaClient.getPolicy(getPolicyRequest);
            statementOpt = Policy.fromJson(GetPolicyResult.getPolicy()).getStatements().stream()
                                                     .filter(statement -> statement.getActions().stream().anyMatch(e -> "lambda:InvokeFunction".equals(e.getActionName())) &&
                                                             statement.getPrincipals().stream().anyMatch(principal -> "sns.amazonaws.com".equals(principal.getId())) &&
                                                             statement.getConditions().stream().anyMatch(condition -> condition.getValues().stream().anyMatch(s -> Objects.equals(createTopicResult.getTopicArn(), s)))
                                                     ).findAny();
        } catch (ResourceNotFoundException ignored) {
            // no policy found
            statementOpt = empty();
        }

        if (!statementOpt.isPresent()) {
            AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                    .withAction("lambda:InvokeFunction")
                    .withPrincipal("sns.amazonaws.com")
                    .withSourceArn(createTopicResult.getTopicArn())
                    .withFunctionName(lambdaFunction.getFunctionName())
                    .withStatementId(UUID.randomUUID().toString());
            AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
            getLog().debug("Added permission to lambda function " + addPermissionResult.toString());
        }
        return trigger;
    };

    /**
     * TODO: Much of this code can be factored out into an addPermission function.
     */
    private BiFunction<Trigger, LambdaFunction, Trigger> addAlexaSkillsKitPermission = (Trigger trigger, LambdaFunction lambdaFunction) -> { 
        if (!ofNullable(lambdaFunction.getExistingPolicy()).orElse(new Policy()).getStatements().stream().anyMatch(s ->
                s.getId().equals(getAlexaPermissionStatementId()))) {
            getLog().info("Granting invoke permission to " + trigger.getIntegration());    
            AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                    .withAction("lambda:InvokeFunction")
                    .withPrincipal("alexa-appkit.amazon.com")
                    .withFunctionName(lambdaFunction.getFunctionName())
                    .withQualifier(lambdaFunction.getQualifier())
                    .withStatementId(getAlexaPermissionStatementId());
    
            AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
        }

        return trigger;
    };
    private String getAlexaPermissionStatementId() {
        return "lambda-maven-plugin-alexa-" + regionName + "-permission";
    }
  
    /**
     * TODO: Much of this code can be factored out into an addPermission function.
     */
    private BiFunction<Trigger, LambdaFunction, Trigger> addLexPermission = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        if (!ofNullable(lambdaFunction.getExistingPolicy()).orElse(new Policy()).getStatements().stream().anyMatch(s ->
                 s.getId().equals(getLexPermissionStatementId(trigger.getLexBotName())))) {
            getLog().info("Granting invoke permission to " + trigger.getLexBotName());
            AddPermissionRequest addPermissionRequest = new AddPermissionRequest()
                .withAction("lambda:InvokeFunction")
                .withPrincipal("lex.amazonaws.com")
                .withFunctionName(lambdaFunction.getFunctionName())
                .withQualifier(lambdaFunction.getQualifier())
                .withStatementId(getLexPermissionStatementId(trigger.getLexBotName()));
    
            AddPermissionResult addPermissionResult = lambdaClient.addPermission(addPermissionRequest);
        }
        return trigger;
    };
    private String getLexPermissionStatementId(String botName) {
        return "lambda-maven-plugin-lex-" + regionName + "-permission-" + botName;
    }

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateScheduledRule = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        // TODO: I hate that these checks are done twice, but for the time being it beats updates that just didn't work.
        if ( isScheduleRuleChanged(lambdaFunction) || isKeepAliveChanged(lambdaFunction)) {
            getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getRuleName());
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
    
    
    private Function<LambdaFunction, LambdaFunction> createOrUpdateKeepAlive = (LambdaFunction lambdaFunction) -> {
        if (isKeepAliveChanged(lambdaFunction)) {
            ofNullable(lambdaFunction.getKeepAlive()).flatMap(f -> {
               if ( f > 0 ) { 
                   getLog().info("Setting keepAlive to " + f + " minutes.");
                   
                   createOrUpdateScheduledRule.apply(new Trigger()
                       .withIntegration("Function Keep Alive")
                       .withDescription(String.format("This feature pings function %s every %d %s.",
                                                       lambdaFunction.getFunctionName(), f,
                                                       f > 1 ? "minutes" : "minute"))
                       .withRuleName(lambdaFunction.getKeepAliveRuleName())        
                       .withScheduleExpression(lambdaFunction.getKeepAliveScheduleExpression()),
                       lambdaFunction);
               }
               
               return Optional.of(f);
            });  
        }
        return lambdaFunction;
    };

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateDynamoDBTrigger = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getDynamoDBTable());
        ListStreamsRequest listStreamsRequest = new ListStreamsRequest().withTableName(trigger.getDynamoDBTable());
        ListStreamsResult listStreamsResult = dynamoDBStreamsClient.listStreams(listStreamsRequest);

        String streamArn = listStreamsResult.getStreams().stream()
                                            .filter(s -> Objects.equals(trigger.getDynamoDBTable(), s.getTableName()))
                                            .findFirst()
                                            .map(Stream::getStreamArn)
                                            .orElseThrow(() -> new IllegalArgumentException("Unable to find stream for table " + trigger.getDynamoDBTable()));

        return findorUpdateMappingConfiguration(trigger, lambdaFunction, streamArn);
    };

    private BiFunction<Trigger, LambdaFunction, Trigger> createOrUpdateKinesisStream = (Trigger trigger, LambdaFunction lambdaFunction) -> {
        getLog().info("About to create or update " + trigger.getIntegration() + " trigger for " + trigger.getKinesisStream());

        try {
            return findorUpdateMappingConfiguration(trigger, lambdaFunction, 
                    kinesisClient.describeStream(trigger.getKinesisStream()).getStreamDescription().getStreamARN());
        } catch (Exception rnfe) {
            throw new IllegalArgumentException("Unable to find stream with name " + trigger.getKinesisStream());
        }        
    };

    private Trigger findorUpdateMappingConfiguration(Trigger trigger, LambdaFunction lambdaFunction, String streamArn) {
        ListEventSourceMappingsRequest listEventSourceMappingsRequest = new ListEventSourceMappingsRequest()
                .withFunctionName(lambdaFunction.getUnqualifiedFunctionArn());
        ListEventSourceMappingsResult listEventSourceMappingsResult = lambdaClient.listEventSourceMappings(listEventSourceMappingsRequest);

        Optional<EventSourceMappingConfiguration> eventSourceMappingConfiguration = listEventSourceMappingsResult.getEventSourceMappings().stream()
                .filter(stream -> {
                    boolean isSameFunctionArn = Objects.equals(stream.getFunctionArn(), lambdaFunction.getUnqualifiedFunctionArn());
                    boolean isSameSourceArn = Objects.equals(stream.getEventSourceArn(), streamArn);
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
    }

    private Function<LambdaFunction, LambdaFunction> createOrUpdateTriggers = (LambdaFunction lambdaFunction) -> {
        lambdaFunction.getTriggers().forEach(trigger -> {
            if (TRIG_INT_LABEL_CLOUDWATCH_EVENTS.equals(trigger.getIntegration())) {
                createOrUpdateScheduledRule.apply(trigger, lambdaFunction);
            } else if (TRIG_INT_LABEL_DYNAMO_DB.equals(trigger.getIntegration())) {
                createOrUpdateDynamoDBTrigger.apply(trigger, lambdaFunction);
            } else if (TRIG_INT_LABEL_KINESIS.equals(trigger.getIntegration())) {
                createOrUpdateKinesisStream.apply(trigger, lambdaFunction);
            } else if (TRIG_INT_LABEL_SNS.equals(trigger.getIntegration())) {
                createOrUpdateSNSTopicSubscription.apply(trigger, lambdaFunction);
            } else if (TRIG_INT_LABEL_ALEXA_SK.equals(trigger.getIntegration())) {
                addAlexaSkillsKitPermission.apply(trigger, lambdaFunction);
            } else if (TRIG_INT_LABEL_LEX.equals(trigger.getIntegration())) {
                addLexPermission.apply(trigger, lambdaFunction);
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
                    VpcConfigResponse vpcConfig = config.getVpcConfig();
                    if (vpcConfig == null) {
                        vpcConfig = new VpcConfigResponse();
                    }
                    boolean isDescriptionChanged = isChangeStr.test(config.getDescription(), lambdaFunction.getDescription());
                    boolean isHandlerChanged = isChangeStr.test(config.getHandler(), lambdaFunction.getHandler());
                    boolean isRoleChanged = isChangeStr.test(config.getRole(), lambdaRoleArn);
                    boolean isTimeoutChanged = isChangeInt.test(config.getTimeout(), lambdaFunction.getTimeout());
                    boolean isMemoryChanged = isChangeInt.test(config.getMemorySize(), lambdaFunction.getMemorySize());
                    boolean isSecurityGroupIdsChanged = isChangeList.test(vpcConfig.getSecurityGroupIds(), lambdaFunction.getSecurityGroupIds());
                    boolean isVpcSubnetIdsChanged = isChangeList.test(vpcConfig.getSubnetIds(), lambdaFunction.getSubnetIds());
                    return isDescriptionChanged || isHandlerChanged || isRoleChanged || isTimeoutChanged || isMemoryChanged || 
                           isSecurityGroupIdsChanged || isVpcSubnetIdsChanged || isAliasesChanged(lambdaFunction) || isKeepAliveChanged(lambdaFunction) ||
                           isScheduleRuleChanged(lambdaFunction);
                })
                .orElse(true);
    }
    
    private boolean isKeepAliveChanged(LambdaFunction lambdaFunction) {
        try {
            return ofNullable(lambdaFunction.getKeepAlive()).map( ka -> {
                DescribeRuleResult res = eventsClient.describeRule(new DescribeRuleRequest().withName(lambdaFunction.getKeepAliveRuleName()));
                return !Objects.equals(res.getScheduleExpression(), lambdaFunction.getKeepAliveScheduleExpression());
            }).orElse(false);
            
        } catch( com.amazonaws.services.cloudwatchevents.model.ResourceNotFoundException ignored ) {
            return true;
        }
    }
    
    private boolean isScheduleRuleChanged(LambdaFunction lambdaFunction) {
        try {
            return lambdaFunction.getTriggers().stream().filter(t -> TRIG_INT_LABEL_CLOUDWATCH_EVENTS.equals(t.getIntegration())).anyMatch(trigger -> {
                DescribeRuleResult res = eventsClient.describeRule(new DescribeRuleRequest().withName(trigger.getRuleName()));
                return !(Objects.equals(res.getName(), trigger.getRuleName()) &&
                        Objects.equals(res.getDescription(), trigger.getRuleDescription()) &&
                        Objects.equals(res.getScheduleExpression(), trigger.getScheduleExpression()));
            });
        } catch( com.amazonaws.services.cloudwatchevents.model.ResourceNotFoundException ignored ) {
            return true;
        }
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
                        .withS3Key(fileName))
                .withEnvironment(new Environment().withVariables(lambdaFunction.getEnvironmentVariables()));

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
    
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedDynamoDBTriggers = lambdaFunction -> { return lambdaFunction; };
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedKinesisTriggers = lambdaFunction -> { return lambdaFunction; };
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedSNSTriggers = lambdaFunction -> { return lambdaFunction; };
    
    /**
     * Removes the Alexa permission if it isn't found in the current configuration.
     * TODO: Factor out code common with other orphan clean up functions.
     */
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedAlexaSkillsTriggers = lambdaFunction -> {
        ofNullable(lambdaFunction.getExistingPolicy()).flatMap( policy -> {
            policy.getStatements().stream()
                .filter(
                    stmt -> stmt.getActions().stream().anyMatch( e -> "lambda:InvokeFunction".equals(e.getActionName())) &&
                    stmt.getPrincipals().stream().anyMatch(principal -> "alexa-appkit.amazon.com".equals(principal.getId())) &&
                    !lambdaFunction.getTriggers().stream().anyMatch( t -> t.getIntegration().equals("Alexa Skills Kit")))
                .forEach( s -> {    
                    try {
                        getLog().info("    Removing orphaned permission for " + s.getId());
                        lambdaClient.removePermission(new RemovePermissionRequest()
                            .withFunctionName(lambdaFunction.getFunctionName())
                            .withQualifier(lambdaFunction.getQualifier())
                            .withStatementId(s.getId()));
                    } catch (ResourceNotFoundException rnfe1) {
                        getLog().error("    Error removing permission for " + s.getId() + ": " + rnfe1.getMessage());
                    }
                });
            return of(policy);
        });
                    
        return lambdaFunction; 
    };
    
    /**
     * Removes any Lex permissions that aren't found in the current configuration.
     * TODO: Factor out code common with other orphan clean up functions.
     */
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedLexSkillsTriggers = lambdaFunction -> {
        ofNullable(lambdaFunction.getExistingPolicy()).flatMap( policy -> {
            policy.getStatements().stream()
                .filter(stmt -> stmt.getActions().stream().anyMatch( e -> "lambda:InvokeFunction".equals(e.getActionName())) &&
                        stmt.getPrincipals().stream().anyMatch(principal -> "lex.amazonaws.com".equals(principal.getId())) &&
                        !lambdaFunction.getTriggers().stream().anyMatch( t -> stmt.getId().contains(t.getLexBotName())))
                .forEach( s -> {    
                    try {
                        getLog().info("    Removing orphaned permission for " + s.getId());
                        lambdaClient.removePermission(new RemovePermissionRequest()
                            .withFunctionName(lambdaFunction.getFunctionName())
                            .withQualifier(lambdaFunction.getQualifier())
                            .withStatementId(s.getId()));
                    } catch (Exception ign2) { 
                        getLog().error("   Error removing permission for " + s.getId() + ign2.getMessage() ); 
                    }
                });
            return of(policy);
        });
        
        return lambdaFunction;
    };
    
    Function<LambdaFunction, LambdaFunction> cleanUpOrphanedCloudWatchEventRules = lambdaFunction -> {
        // Get the list of cloudwatch event rules defined for this function (if any).
        List<String> existingRuleNames = cloudWatchEventsClient.listRuleNamesByTarget(new ListRuleNamesByTargetRequest()
        .withTargetArn(lambdaFunction.getFunctionArn())).getRuleNames();
    
        // Get the list of cloudwatch event rules to be defined for this function (if any).
        List<String> definedRuleNames = lambdaFunction.getTriggers().stream().filter(
                t -> TRIG_INT_LABEL_CLOUDWATCH_EVENTS.equals(t.getIntegration())).map(t -> {
                    return t.getRuleName();
                }).collect(toList());
        
        // Add the keep alive rule name if the user has disabled keep alive for the function.
        ofNullable(lambdaFunction.getKeepAlive()).ifPresent(ka -> {
           if ( ka > 0 ) {
               definedRuleNames.add(lambdaFunction.getKeepAliveRuleName());
           }
        });
    
        // Remove all of the rules that will be defined from the list of existing rules.
        // The remainder is a set of event rules which should no longer be associated to this
        // function.
        existingRuleNames.removeAll(definedRuleNames);
        
        // For each remaining rule, remove the function as a target and attempt to delete
        // the rule.
        existingRuleNames.stream().forEach(ern -> {
            getLog().info("    Removing CloudWatch Event Rule: " + ern);
            cloudWatchEventsClient.removeTargets(new RemoveTargetsRequest()
                .withIds("1")
                .withRule(ern));
            try {
                cloudWatchEventsClient.deleteRule(new DeleteRuleRequest().withName(ern));
            } catch (Exception e) {
                getLog().error("    Error removing orphaned rule: " + e.getMessage());
            }
        });
        
        return lambdaFunction;  
    };
    
    Function<LambdaFunction, LambdaFunction> cleanUpOrphans = lambdaFunction -> {
        try {
            lambdaFunction.setFunctionArn(lambdaClient.getFunction(
                    new GetFunctionRequest().withFunctionName(lambdaFunction.getFunctionName())).getConfiguration().getFunctionArn());
            
            getLog().info("Cleaning up orphaned triggers.");
            
            // TODO: Add clean up orphaned trigger functions for each integration here:
            cleanUpOrphanedCloudWatchEventRules
                .andThen(cleanUpOrphanedDynamoDBTriggers)
                .andThen(cleanUpOrphanedKinesisTriggers)
                .andThen(cleanUpOrphanedSNSTriggers)
                .andThen(cleanUpOrphanedAlexaSkillsTriggers)
                .andThen(cleanUpOrphanedLexSkillsTriggers)
                .apply(lambdaFunction);
            
        } catch (ResourceNotFoundException ign1) {
            getLog().debug("Assuming function has no orphan triggers to clean up since it doesn't exist yet.");
        }
            
        return lambdaFunction;  
    };

    Function<LambdaFunction, LambdaFunction> createOrUpdate = lambdaFunction -> {
      try {
          lambdaFunction.setFunctionArn(lambdaClient.getFunction(
                  new GetFunctionRequest().withFunctionName(lambdaFunction.getFunctionName())).getConfiguration().getFunctionArn());
          of(getFunction(lambdaFunction))
                  .filter(getFunctionResult -> shouldUpdate(lambdaFunction, getFunctionResult))
                  .map(getFujnctionResult ->
                  updateFunctionCode
                          .andThen(updateFunctionConfig)
                          .andThen(createOrUpdateAliases)
                          .andThen(createOrUpdateTriggers)
                          .andThen(createOrUpdateKeepAlive)
                          .apply(lambdaFunction));
      } catch (ResourceNotFoundException ign) {
          createFunction.andThen(createOrUpdateAliases)
                        .andThen(createOrUpdateTriggers)
                        .apply(lambdaFunction);
      }
                      
      return lambdaFunction;
    };

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
        if (s3Client.listBuckets().stream().noneMatch(p -> Objects.equals(p.getName(), s3Bucket))) {
            getLog().info("Created bucket s3://" + s3Client.createBucket(s3Bucket).getName());
        }
        return s3Bucket;
    }
}
