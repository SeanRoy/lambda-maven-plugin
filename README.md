# lambduh-maven-plugin

The lambduh Maven plugin allows you to deploy your [AWS Lambda](http://aws.amazon.com/lambda/) functions
as a part of your usual Maven build process.

### Usage
`group id: snr.plugins`<br />
`artifact id: lambduh-maven-plugin`<br />
`version:1.0.0`<br />

`mvn lambduh:deploy-lambda`

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

### Caveats
As of 7/20/2015, this has yet to be tested with NodeJs code.

### TODO
* Allow upload of function code directly to AWS Lambda instead of using S3.
