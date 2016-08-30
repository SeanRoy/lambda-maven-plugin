package no.cantara.maven.plugins;


import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * I am a delete mojo responsible for deleteing lambda function configuration and code from AWS.
 *
 * @author Sean N. Roy
 */
@Mojo(name = "delete-lambda")
public class DeleteMojo extends AbstractLambdaMojo {

    /**
     * The entry point into the AWS lambda function.
     */
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            lambdaFunctions.forEach(context -> {
                try {
                    deleteFunction(context.getFunctionName());
                } catch (Exception e) {
                    getLog().error(e.getMessage());
                }
            });
        } catch (Exception e) {
            getLog().error(e.getMessage(), e);
        }
    }

    /**
     * Deletes the lambda function from AWS Lambda and removes the function code
     * package from S3.
     * <p>
     * TODO: Make this more sophisticated by checking for the existence of the
     * TODO: resources first, or reacting to the ResourceNotFoundException. I
     * TODO: prefer the first option.
     * </p>
     *
     * @param functionName to delete
     * @throws Exception the exception from AWS API
     */
    private void deleteFunction(String functionName) throws Exception {
        // Delete Lambda Function
        DeleteFunctionRequest dfr = new DeleteFunctionRequest().withFunctionName(functionName);

        lambdaClient.deleteFunction(dfr);
        getLog().info("Lambda function " + functionName + " successfully deleted.");

        s3Client.deleteObject(s3Bucket, fileName);
        getLog().info("Lambda function code successfully removed from S3.");
    }
}
