package com.github.seanroy.plugins;

import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.function.Function;

/**
 * I am a update code mojo responsible to update lambda function code in AWS.
 *
 * @author Joseph Wortmann, <a href="mailto:joseph.wortmann@gmail.com">Joseph Wortmann</a> 2/1/2018.
 */
@Mojo(name = "update-lambda-code")
public class UpdateLambdaCodeMojo extends AbstractLambdaMojo {

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            uploadJarToS3();
            lambdaFunctions.stream().map(f -> {
                getLog().info("---- Update function code " + f.getFunctionName() + " -----");
                return f;
            }).forEach(lf -> updateFunctionCodeIfExists.apply(lf));
        } catch (Exception e) {
            getLog().error("Error during processing", e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private Function<LambdaFunction, LambdaFunction> updateFunctionCodeIfExists = (LambdaFunction lambdaFunction) -> {
        try {
            lambdaFunction.setFunctionArn(lambdaClient.getFunction(
                    new GetFunctionRequest().withFunctionName(lambdaFunction.getFunctionName())).getConfiguration().getFunctionArn());
            updateFunctionCode.apply(lambdaFunction);
        } catch (ResourceNotFoundException e) {
            getLog().info("Lambda function not found", e);
        }
        return lambdaFunction;
    };
}
