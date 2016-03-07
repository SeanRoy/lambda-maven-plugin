# lambduh-maven-plugin

The lambduh Maven plugin allows you to deploy your [AWS Lambda](http://aws.amazon.com/lambda/) functions
as a part of your usual Maven build process.

### Usage
`group id: com.github.seanroy`<br />
`artifact id: lambduh-maven-plugin`<br />
`version:1.1.0`<br />

`mvn lambduh:deploy-lambda`  Deploy lambduh code <br/>
`mvn lambduh:delete-lambda`  Delete lambduh code from Lambda and S3 <br/>

### Configuration
All of the AWS Lambda configuration parameters may be set within the lambduh plugin configuration or
on the Maven command line using the -D directive.

* `accessKey` Your user's AWS access key.
* `secretKey` Your user's AWS secret access key.
* `region` Defaults to us-east-1 The AWS region to use for your function.
* `functionName` REQUIRED The name of your function.
* `functionCode` REQUIRED The location of your deliverable. For instance, a jar file for a Java8 lambda function.
${project.build.directory}/${project.build.finalName}.${project.packaging}
* `description` A brief description of what your function does.
* `s3Bucket` Defaults to lambda-function-code. The AWS S3 bucket to which to upload your code from which it will be deployed to Lambda.
* `lambdaRoleArn` REQUIRED The ARN of the AWS role which the lambda user will assume when it executes.
* `handler` REQUIRED The entry point method of your function. ex. com.example.mycode.MyClass::codeHandler.
* `runtime` Defaults to Java8 Specifies whether this is Java8 or NodeJs code.
* `timeout` Defaults to 60 seconds The amount of time in which the function is allowed to run.
* `memorySize` Defaults to 128M NOTE: Please review the AWS Lambda documentation on this setting as it could have an impact on your billing.

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

### Caveats
As of 7/20/2015, this has yet to be tested with NodeJs code.

### TODO
* Allow upload of function code directly to AWS Lambda instead of using S3.
* Add ability to update/delete functions.

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
