package com.github.seanroy.plugins;

import static java.util.Optional.of;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Test;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;

public class LambdaTest extends AbstractMojoTestCase {

    private AWSCredentials credentials;
    public AWSLambdaClient lambdaClient;
    private String accessKey = null;
    private String secretKey = null;
    private String regionName = "us-east-1";
    
    protected void setUp()
        throws Exception
    {
        super.setUp();
        
        Regions region = Regions.fromName(regionName);
        
        DefaultAWSCredentialsProviderChain defaultChain = new DefaultAWSCredentialsProviderChain();
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        } else if (defaultChain.getCredentials() != null) {
            credentials = defaultChain.getCredentials();
        }

        if (credentials == null) {
            throw new MojoExecutionException("AWS Credentials config error");
        }
        
        lambdaClient = of(credentials)
                .map(credentials -> new AWSLambdaClient(credentials).<AWSLambdaClient>withRegion(region))
                .orElse(new AWSLambdaClient().withRegion(region));
    }

    protected void tearDown()
        throws Exception
    {
        super.tearDown();
    }
    
    @Test
    public void testNOOP() {
      assertTrue(true);
    }
 
    @Test
    public void testBasic() throws Exception {
        File pom = getTestFile("src/test/resources/test-projects/basic-test/basic-pom.xml");
        assertNotNull( pom );
        assertTrue( pom.exists() );

        DeployLambdaMojo lambduhMojo = (DeployLambdaMojo) lookupMojo( "deploy-lambda", pom );
        assertNotNull( lambduhMojo );
        lambduhMojo.execute();
   
        DeleteLambdaMojo deleteMojo = (DeleteLambdaMojo) lookupMojo( "delete-lambda", pom);
        assertNotNull( deleteMojo );
        deleteMojo.execute();
       
    }

}
