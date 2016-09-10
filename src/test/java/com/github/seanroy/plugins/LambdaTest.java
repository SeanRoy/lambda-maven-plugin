package com.github.seanroy.plugins;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Test;

public class LambdaTest extends AbstractMojoTestCase {

    protected void setUp()
        throws Exception
    {
        super.setUp();
    }

    protected void tearDown()
        throws Exception
    {
        super.tearDown();
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
