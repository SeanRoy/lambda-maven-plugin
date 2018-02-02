package com.github.seanroy.plugins;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

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
            }).forEach(lf -> updateFunctionCode.apply(lf));
        } catch (Exception e) {
            getLog().error("Error during processing", e);
            throw new MojoExecutionException(e.getMessage());
        }
    }
}
