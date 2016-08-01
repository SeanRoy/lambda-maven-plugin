package com.github.seanroy.plugins;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.scannotation.AnnotationDB;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.github.seanroy.annotations.LambduhFunction;


/**
 * Abstracts all common parameter handling and initiation of AWS service
 * clients.
 * 
 * @author sean
 *
 */
public abstract class AbstractLambduhMojo extends AbstractMojo {
    @Parameter(property = "accessKey", defaultValue = "${accessKey}")
    protected String accessKey;

    @Parameter(property = "secretKey", defaultValue = "${secretKey}")
    protected String secretKey;

    @Parameter(required = true, defaultValue = "${functionCode}")
    protected String functionCode;

    @Parameter(alias = "region", property = "region", defaultValue = "us-east-1")
    protected String regionName;

    @Parameter(property = "s3Bucket", defaultValue = "lambda-function-code")
    protected String s3Bucket;

    @Parameter(property = "description", defaultValue = "")
    protected String description;

    @Parameter(required = true, defaultValue = "${lambdaRoleArn}")
    protected String lambdaRoleArn;

    @Parameter(property = "functionName", defaultValue = "${functionName}")
    protected String functionName;

    @Parameter(property = "handler", defaultValue = "${handler}")
    protected String handler;

    @Parameter(property = "runtime", defaultValue = "Java8")
    protected String runtime;

    /**
     * Lambda function execution timeout. Defaults to maximum allowed.
     */
    @Parameter(property = "timeout", defaultValue = "60")
    protected int timeout;

    @Parameter(property = "memorySize", defaultValue = "128")
    protected int memorySize;

    @Parameter(property = "vpcSecurityGroupsIds")
    protected List<String> vpcSecurityGroupsIds;

    @Parameter(property = "vpcSubnetIds")
    protected List<String> vpcSubnetIds;

    protected String fileName;
    protected Region region;

    protected AWSCredentials credentials;
    protected AmazonS3Client s3Client;
    protected AWSLambdaClient lambdaClient;
    
    protected List<LambduhFunctionContext> lambduhFunctionContexts = new ArrayList<LambduhFunctionContext>();
    
    public void execute() throws MojoExecutionException {
        DefaultAWSCredentialsProviderChain defaultChain = 
                new DefaultAWSCredentialsProviderChain();
        
        if (accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        }
        else if (defaultChain.getCredentials()!=null)
        {
            credentials = defaultChain.getCredentials();
        }
    
        s3Client = (credentials==null) ? 
                new AmazonS3Client():new AmazonS3Client(credentials);
        lambdaClient = (credentials==null) ?
                new AWSLambdaClient():new AWSLambdaClient(credentials);   
    
        region = Region.getRegion(Regions.fromName(regionName));
        lambdaClient.setRegion(region);
        
        String pattern = Pattern.quote(File.separator);
        String[] pieces = functionCode.split(pattern);
        fileName = pieces[pieces.length - 1];
        
        try {
            if (handler == null || handler.isEmpty()) {
                resolveFunctionsFromAnnotations();
            } else {
                if (functionName == null || functionName.isEmpty()) {
                    throw new Exception(
                      "<functionName> must be specified when <handler> is specified. " +
                      "Please review your lambduh-maven-plugin configuration");
                }
                lambduhFunctionContexts.add(
                        new LambduhFunctionContext(functionName, description, runtime, handler));
            }
        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException(e.getMessage());
        }
    }
    
    /**
     * If the user has opted to use annotations to define the methods they wish to deploy,
     * this method scans their jar file for LambduhFunction annotations and builds a list
     * of LambduhFunctionContexts from what it finds.
     */
    private void resolveFunctionsFromAnnotations() throws Exception {
        try {
            // Scan Jar for LambdaFunction annotations.
            JarFile jarFile = new JarFile(functionCode);
            URL [] urls = { new URL("jar:file:" + functionCode + "!/")};
            AnnotationDB db = new AnnotationDB();
            db.setScanMethodAnnotations(true);
            getLog().info("Scanning " + functionCode + " for LambdaFunction annotations.");
            db.scanArchives(urls);
            Set<String> s = db.getAnnotationIndex().get(LambduhFunction.class.getName());
            Iterator<String> iter = s.iterator();
            
            URLClassLoader classLoader = URLClassLoader.newInstance(urls);
            Class loadedLambduhFunction = classLoader.loadClass(LambduhFunction.class.getName());
            
            // This invocation handler mess is necessary in order to properly load annotations in jars
            // shaded by the maven shade plugin.  Review this code in the future to see if there's a
            // simpler way of doing this.
            while(iter.hasNext()) {
                String className = iter.next();
                
                getLog().info("\tScanning " + className);
                
                try {
                    Class c = classLoader.loadClass(className);
                    
                    Arrays.stream(c.getMethods()).forEach(method -> {                          
                        Annotation lambduhFunctionAnnotation = method.getAnnotation(loadedLambduhFunction);
                        
                        if (lambduhFunctionAnnotation != null) {
                            InvocationHandler lambduhFunctionInvocationHandler = 
                                    Proxy.getInvocationHandler(lambduhFunctionAnnotation);
                            
                            try {
                                String functionNameOverride = (String) 
                                        lambduhFunctionInvocationHandler.invoke(lambduhFunctionAnnotation, 
                                                    LambduhFunction.class.getMethod("functionName"), null);
                                String description = (String) 
                                        lambduhFunctionInvocationHandler.invoke(lambduhFunctionAnnotation, 
                                                    LambduhFunction.class.getMethod("description"), null);
                                String runtime = (String) 
                                        lambduhFunctionInvocationHandler.invoke(lambduhFunctionAnnotation, 
                                                    LambduhFunction.class.getMethod("runtime"), null);
                                
                                String annotatedHandler = className + "::" + method.getName();
                                
                                getLog().info("\tFound annotated method " + method.getName());
                                
                                lambduhFunctionContexts.add(
                                        new LambduhFunctionContext(functionNameOverride, 
                                                description,
                                                runtime,
                                                annotatedHandler));
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    });
                    
                    if ( lambduhFunctionContexts.isEmpty() ) {
                        getLog().error("Could not find any LambduhFunction annotations in your code!");
                    }
                } catch(ClassNotFoundException cnfe) {
                    getLog().error("Unable to load class " + className + " for annotation scanning.");
                    getLog().error(cnfe.getMessage());
                    cnfe.printStackTrace();
                } 
            }
        } catch( MalformedURLException mfe ) {
            mfe.printStackTrace();
        } catch( IOException ioe ) {
            ioe.printStackTrace();
        }
    }
}
