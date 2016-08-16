package no.cantara.maven.plugins;

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
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * A Maven plugin allowing a jar built as a part of a Maven project to be
 * deployed to AWS lambda.
 *
 * @author Sean N. Roy
 */
@Mojo(name = "deploy-lambda")
public class DeployLambduhMojo extends AbstractLambduhMojo {

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            uploadJarToS3();
            lambdaFunctions.forEach(this::createOrUpdate);
        } catch (Exception e) {
            getLog().error("Error during processing", e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private Void createOrUpdate(LambdaFunction lambdaFunction) {
        try {
            try {
                of(getFunction(lambdaFunction))
                        .filter(getFunctionResult -> shouldUpdate(lambdaFunction, getFunctionResult))
                        .map(getFunctionResult -> updateFunctionCode.andThen(updateFunctionConfig).andThen(createOrUpdateAliases).apply(lambdaFunction));
            } catch (ResourceNotFoundException ignored) {
                createFunction.andThen(createOrUpdateAliases).apply(lambdaFunction);
            }
        } catch (Exception ex) {
            getLog().error("Error getting / creating / updating function", ex);
        }
        return null;
    }

    private boolean shouldUpdate(LambdaFunction lambdaFunction, GetFunctionResult getFunctionResult) {
        boolean isDev = Alias.DEV.equals(alias);
        boolean isConfigurationChanged = isConfigurationChanged(lambdaFunction, getFunctionResult);
        if (!isConfigurationChanged) {
            getLog().info("Config hasn't changed for " + lambdaFunction.getFunctionName());
        }
        if (isDev) {
            getLog().info("Forcing update for alias " + alias.toString() + " for " + lambdaFunction.getFunctionName());
        }

        return isDev || isConfigurationChanged;
    }

    private Function<LambdaFunction, LambdaFunction> updateFunctionCode = (LambdaFunction lambdaFunction) -> {
        getLog().info("About to update functionCode for " + lambdaFunction.getFunctionName());
        Function<String, Boolean> shouldPublish = (String version) -> ofNullable(lambdaFunction.getVersion()).map(v -> !v.contains("SNAPSHOT")).orElse(true);
        UpdateFunctionCodeRequest updateFunctionRequest = new UpdateFunctionCodeRequest()
                .withFunctionName(lambdaFunction.getFunctionName())
                .withS3Bucket(s3Bucket)
                .withS3Key(fileName)
                .withPublish(shouldPublish.apply(lambdaFunction.getVersion()));
        return lambdaFunction.withVersion(lambdaClient.updateFunctionCode(updateFunctionRequest).getVersion());
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

    private Function<LambdaFunction, List<String>> createOrUpdateAliases = (LambdaFunction lambdaFunction) ->
            lambdaFunction.getAliases().stream().map(alias -> {
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
                return alias;
            }).collect(toList());

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
                    boolean isSecurityGroupIdsChanged = isChangeList.test(config.getVpcConfig().getSecurityGroupIds(), lambdaFunction.getSecurityGroupsIds());
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
        lambdaFunction.withVersion(createFunctionResult.getVersion());
        getLog().info("Function " + createFunctionResult.getFunctionName() + " created. Function Arn: " + createFunctionResult.getFunctionArn());
        return lambdaFunction;
    };

    private VpcConfig getVpcConfig(LambdaFunction lambdaFunction) {
        return new VpcConfig()
                .withSecurityGroupIds(lambdaFunction.getSecurityGroupsIds())
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
                    } else {
                        upload(file);
                    }
                    return true;
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
        if (s3Client.doesBucketExist(s3Bucket)) {
            return s3Bucket;
        } else {
            throw new IllegalArgumentException(s3Bucket + " does not exist. Create me first...");
        }
    }
}
