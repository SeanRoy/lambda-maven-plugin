# lambduh-maven-plugin

You are viewing the current snapshot's README.  To read the README of the latest stable release please go here [README.md](https://github.com/SeanRoy/lambduh-maven-plugin/blob/master/README.md)

The lambduh Maven plugin allows you to deploy your [AWS Lambda](http://aws.amazon.com/lambda/) functions
as a part of your usual Maven build process.

### Usage
`group id: com.github.seanroy`<br />
`artifact id: lambduh-maven-plugin`<br />
`version:1.1.4`<br />

`mvn lambduh:deploy-lambda`  Deploy lambda code <br />
`mvn lambduh:delete-lambda`  Delete lambda code from Lambda and S3 <br />

### Configuration
All of the AWS Lambda configuration parameters may be set within the lambduh plugin configuration or
on the Maven command line using the -D directive.

* `accessKey` Your user's AWS access key.
* `secretKey` Your user's AWS secret access key.
* `region` Defaults to us-east-1 The AWS region to use for your function.
* `handler` REQUIRED if not using Annotations. The entry point method of your function. ex. com.example.mycode.MyClass::codeHandler.
* `functionName` REQUIRED if not using Annotations. REQUIRED if handler specified. The name of your function.
* `functionCode` REQUIRED The location of your deliverable. For instance, a jar file for a Java8 lambda function.
${project.build.directory}/${project.build.finalName}.${project.packaging}
* `description` A brief description of what your function does.
* `s3Bucket` Defaults to lambda-function-code. The AWS S3 bucket to which to upload your code from which it will be deployed to Lambda.
* `lambdaRoleArn` REQUIRED The ARN of the AWS role which the lambda user will assume when it executes.
* `runtime` Defaults to Java8 Specifies whether this is Java8 or NodeJs code.
* `timeout` Defaults to 60 seconds The amount of time in which the function is allowed to run.
* `memorySize` Defaults to 128M NOTE: Please review the AWS Lambda documentation on this setting as it could have an impact on your billing.
* `vpcSecurityGroupIds` OPTIONAL A list of one or more ids corresponding to the security groups protecting access to your AWS VPC.
* `vpcSubnetIds` OPTIONAL A list of subnet ids within your AWS VPC.

Annotations may also be used to specify which of your functions are to be deployed to AWS Lambda.  This is useful
if you have multiple functions in the same project jar file to deploy.  In the future I hope to add more configuration parameters to
the annotation to allow each method's configuration to be completely independent of the others.

To use Annotations, add the following dependency to your project pom.xml:<br />
`group id: com.github.seanroy`<br />
`artifact id: lambduh-maven-annotations`<br />
`version:1.0.0`<br />

Here is an example of annotated code:

```java
import com.github.seanroy.annotations.*;
/**
 * Hello world!
 *
 */
public class App
{
    @LambduhFunction(functionName="Hello-World", runtime="Java8", description="Hello World test")
    public static void hello_world( String[] args )
    {
        System.out.println( "Hello World!" );
    }
    
    @LambduhFunction(functionName="Goodbye-World", runtime="Java8", description="Goodbye World test")
    public static void goodbye_world( String [] args ) {
        System.out.println( "Goodbye World!" );
    }
}
```

Additionally, you must not have the `<functionName>` and `<handler>` configuration parameters in your
lambduh-maven-plugin configuration.

### Credentials
Your AWS credentials may be set on the command line or in the plugin configuration. If `accessKey` and
`secretKey` are not explicitly defined, the plugin will look for them in your environment or in your
~/.aws/credentials file

IAM permissions required by this plugin:
* actions `s3:GetObject` and `s3:PutObject` on resource `arn:aws:s3:::<s3Bucket>/*`
* action `s3:ListBucket` on resource `arn:aws:s3:::<s3Bucket>`
* action `s3:CreateBucket` if you want the plugin to create the S3 bucket you specify.
* action `lambda:CreateFunction`
* action `lambda:InvokeFunction`
* action `lambda:DeleteFunction`
* action `lambda:GetFunction`
* action `lambda:UpdateFunctionCode`
* action `lambda:UpdateFunctionConfiguration``

### Caveats
As of 7/20/2015, this has yet to be tested with NodeJs code.

### TODO
* Allow upload of function code directly to AWS Lambda instead of using S3.

### Developers
If you are interested in contributing to this project, please note that current development can be found in the SNAPSHOT branch of the coming release.  When making pull requests, please create them against this branch.

A test harness has been provided which can be run with `mvn test` Please use 
this and feel free to add additional tests. Note that the basic-pom.xml file
requires you to add your role arn in order to work.  As such, basic-pom.xml
has been added to .gitignore so that you don't accidentally commit your role
to the file.  If you add more pom's as part of enhancing the test suite,
please remember to add them to .gitignore.

### Releases
1.0.2 
* Fixed PatternSyntaxException on windows https://github.com/SeanRoy/lambduh-maven-plugin/issues/1

1.0.3 
* Fixed a bug where getting a bucket fails if existing. Thanks buri17
* Fixed problem with region specification. Thanks buri17
* Adding ability to pull creds from the default provider. Thanks Chris Weiss

1.0.4
* Fixed issue 8
* No longer uploads function code to S3 when no changes have been made to speed up
  development cycle over slow connections.  Thanks Philip M. White.
* Fixed logging.

1.0.5
* Accidental deployment of release.  Should be functionally equivalent to 
1.0.4.

1.0.6
* Issue 19 Added test harness.
* Update function code if code or configuration has changed instead of
deleting and recreating every time.  Thanks Guillermo Menendez

1.1.0
* Added delete goal.

1.1.1
* Added support for Virtual Private Clouds. Thanks Jem Rayfield.
* Added ability to designate functions for deployment via LambduhFunction annotations. (Details coming soon).

1.1.2
* Fixed invalid dependency to lambduh-maven-annotations

1.1.3
* Fixed [Issue 28] (https://github.com/SeanRoy/lambduh-maven-plugin/issues/28) 
