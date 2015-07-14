package snr.plugins;

/**
 * @author Sean N. Roy
 */

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;

@Mojo(name = "lambda-function")
public class LambduhMojo
    extends AbstractMojo
{
	final Logger logger = LoggerFactory.getLogger( LambduhMojo.class );
	
	@Parameter(defaultValue = "${accessKey}")
	private String accessKey;
	
	@Parameter(defaultValue = "${secretKey}")
	private String secretKey;
	
	@Parameter(defaultValue = "${jarFile}")
	private String jarFile;
	
	@Parameter(defaultValue = "${lambdaRole}")
	private String lambdaRole;
	
	@Parameter(property = "region", defaultValue = "us-east-1")
	private String regionName;
	
	@Parameter(property = "s3Bucket", defaultValue = "nrby-lambda-functions")
	private String s3Bucket;
	
	private Region region;
	
	private AWSCredentials credentials;
	private AmazonS3Client s3Client;
	private AWSLambdaClient lambdaClient;
	
    public void execute() throws MojoExecutionException
    {
    	credentials = new BasicAWSCredentials(accessKey, secretKey);
    	s3Client = new AmazonS3Client(credentials);
    	lambdaClient = new AWSLambdaClient(credentials);
    	
    	region = Region.getRegion(Regions.fromName(regionName));
    	
    	try {
	    	uploadJarToS3(credentials);
	    	
	    	lambdaClient.setRegion(region);
    	} catch(Exception e) {
    		logger.error(e.getMessage());
    		logger.trace(e.getMessage(), e);
    	}
    }
    
    private void uploadJarToS3(AWSCredentials credentials) throws Exception {
    	Bucket bucket = getBucket();
    	
    	if (bucket != null) {
    		File file = new File(jarFile);
    		String [] pieces = jarFile.split(File.separator);
    		
    		logger.info("Uploading " + jarFile + " to AWS S3 bucket " + s3Bucket);
    		s3Client.putObject(s3Bucket, pieces[pieces.length-1], file);
    		logger.info("Upload complete");
    		
    	} else {
    		logger.error("Failed to create bucket " + s3Bucket + 
    				     "try running maven with -X to get full " +
    				     "debug output");
    	}
    	
    }
    
    private Bucket getBucket() {
    	Bucket bucket = null;
    	
    	try {
    		bucket = s3Client.createBucket(s3Bucket, 
    				com.amazonaws.services.s3.model.Region.US_Standard);
    		logger.info("Created bucket " + s3Bucket);
    	} catch (AmazonServiceException ase) {
    		logger.error(ase.getMessage());
    	} catch (AmazonClientException ace) {
    		logger.error(ace.getMessage());
    	}
    	
    	return bucket;
    }
}
