package com.github.seanroy.plugins;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Test;

public class LambduhTest extends AbstractMojoTestCase {

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
        File pom = getTestFile("src/test/resources/test-project/basic-pom.xml");
        assertNotNull( pom );
        assertTrue( pom.exists() );

        DeployLambduhMojo lambduhMojo = (DeployLambduhMojo) lookupMojo( "deploy-lambda", pom );
        assertNotNull( lambduhMojo );
        lambduhMojo.execute();
        
        DeleteLambduhMojo deleteMojo = (DeleteLambduhMojo) lookupMojo( "delete-lambda", pom);
        assertNotNull( deleteMojo );
        deleteMojo.execute();
    }
}
