# lambduh-maven-plugin

The lambduh Maven plugin allows you to deploy your [AWS Lambda](http://aws.amazon.com/lambda/) functions
as a part of your usual Maven build process.

### Configuration
All of the AWS Lambda configuration parameters may be set within the lambduh plugin configuration or
on the Maven command line using the -D directive.

*`accessKey`
*`secretKey`
*`jarFile`
*`region Defaults to us-east-1`
*`functionName`
*`description`
*`s3Bucket`
*`lambdaRoleArn`
*`handler`
*`runtime Defaults to Java8`
*`timeout Defaults to 60 seconds`
*`memorySize Defaults to 128M`

### Credentials
Your AWS credentials may be set on the command line or in the plugin configuration. If `accessKey` and
`secretKey` are not explicitly defined, the plugin will look for them in your environment or in your
~/.aws/credentials file

