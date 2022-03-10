# lambda-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.seanroy/lambda-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.seanroy/lambda-maven-plugin)
<br/>
### Usage
`group id: com.github.seanroy`<br />
`artifact id: lambda-maven-plugin`<br />
`version: 2.3.5`<br />
<br/><br/>
Please note that the artifact has been renamed from lambduh-maven-plugin to
lambda-maven-plugin.

### Deploy from command line
```
mvn package shade:shade lambda:deploy-lambda
```

### Delete from command line
```
mvn lambda:delete-lambda
```

### Configuration
All of the AWS Lambda configuration parameters may be set within the lambda plugin configuration or on the Maven command line using the -D directive.

* `accessKey` Your user's AWS access key.
* `secretKey` Your user's AWS secret access key.
* `functionCode` REQUIRED The location of your deliverable. For instance, a jar file for a Java8 lambda function.
* `version` REQUIRED version of the deliverable. Note that this is the version you assign to your function, not the one assigned by AWS when publish=true.
* `alias` OPTIONAL, but requires publish=true.  Assigns an alias to the AWS version of this function.  Useful for maintaining versions intended for different environments on the same function.  For instance, development, qa, production, etc.
* `s3Bucket` REQUIRED Defaults to lambda-function-code. The AWS S3 bucket to which to upload your code from which it will be deployed to Lambda.
* `sse` OPTIONAL Turns on Server Side Encryption when uploading the function code
* `sseKmsEncryptionKeyArn` OPTIONAL Specifies a kms arn used to encrypt the lambda code, if desired.
* `keyPrefix` OPTIONAL Specifies the key prefix to use when uploading the function code jar. Defaults to "/"
* `region` Defaults to us-east-1 The AWS region to use for your function.
* `runtime` Defaults to Java8 Specifies whether this is Java8, NodeJs and Python.
* `lambdaRoleArn` The ARN of the AWS role which the lambda user will assume when it executes. Note that the role must be assumable by Lambda and must have Cloudwatch Logs permissions and AWSLambdaDynamoDBExecutionRole policy.
* `lambdaFunctions` Lamda functions that can be configured using tags in pom.xml.
* `lambdaFunctionsJSON` JSON configuration for Lambda Functions. This is preferable configuration.
* `timeout` Defaults to 30 seconds. The amount of time in which the function is allowed to run.
* `memorySize` Defaults to 1024MB NOTE: Please review the AWS Lambda documentation on this setting as it could have an impact on your billing.
* `vpcSubnetIds` The VPC Subnets that Lambda should use to set up your VPC configuration. Format: "subnet-id (cidr-block) | az name-tag".
* `vpcSecurityGroupIds` The VPC Security Groups that Lambda should use to set up your VPC configuration. Format: "sg-id (sg-name) | name-tag". Should be configured.
* `publish` This boolean parameter can be used to request AWS Lambda to update the Lambda function and publish a version as an atomic operation. This is global for all functions and won't overwrite publish paramter in provided Lambda configuration. Setting to false will only update $LATEST.
* `functionNameSuffix` The suffix for the lambda function. Function name is automatically suffixed with it. When left blank no suffix will be applied.
* `forceUpdate` This boolean parameter can be used to force update of existing configuration. Use it when you don't publish a function and want to deploy code in your Lambda function. This is automatically set to `true` if the version contains `SNAPSHOT`.
* `triggers` A list of one or more triggers that execute Lambda function. Currently `CloudWatch Events - Schedule`, `SNS`, `SQS`, `DynamoDB` and `Kinesis` are supported. When `functionNameSuffix` is present then suffix will be added automatically.
* `environmentVariables` Map to define environment variables for Lambda functions enable you to dynamically pass settings to your function code and libraries, without making changes to your code. Deployment functionality merges those variables with the one provided in json configuration.
* `keepAlive` When specified, a CloudWatch event is scheduled to "ping" your function every X minutes, where X is the
 value you specify.  This keeps your lambda function resident and ready to receive real requests at all times.  This is
 useful for when you need your function to be responsive.
* `passThrough` This directive is to be used only on the command line.  It allows you to pass environment variables from the command line to your functions using json. Example:
```
mvn package shade:shade lambda:deploy-lambda -DpassThrough="{'KEY1' : 'VAL1', 'KEY2' : 'VAL2'}"
```
* `kmsEncryptionKeyArn` The AWS KMS encryption key you wish to use to encrypt/decrypt sensitive environment variables.
* `encryptedPassThrough` Similar to passThrough (see above), but these variables will be encrypted using the KMS encryption key specified above. Requires that kmsEncryptionKeyArn is specified.
* `clientConfiguration` Allows you to specify a http(s) proxy when communicating with AWS. The following parameters may be specified, see the Example configuration below for an example.
  * `protocol`
  * `proxyHost`
  * `proxyPort`
  * `proxyDomain`
  * `proxyUsername`
  * `proxyPassword`
  * `proxyWorkstation`

Current configuration of LambdaFunction can be found in LambdaFunction.java.

### Example configuration in pom.xml
```
        <project

            ...

            <properties>
                <lambda.functionCode>${project.build.directory}/${project.build.finalName}.jar</lambda.functionCode>
                <lambda.version>${project.version}</lambda.version>
                <lambda.publish>true</lambda.publish>
                <lambda.forceUpdate>true</lambda.forceUpdate>
                <lambda.functionNameSuffix>dev</lambda.functionNameSuffix>
            </properties>

           ...

            <plugin>
                    <groupId>com.github.seanroy</groupId>
                    <artifactId>lambda-maven-plugin</artifactId>
                    <version>2.3.2</version>
                    <configuration>
                        <functionCode>${lambda.functionCode}</functionCode>
                        <version>${lambda.version}</version>
                        <alias>development</alias>
                        <vpcSecurityGroupIds>sg-123456</vpcSecurityGroupIds>
                        <vpcSubnetIds>subnet-123456,subnet-123456,subnet-123456</vpcSubnetIds>
                        <lambdaRoleArn>arn:aws:iam::1234567:role/YourLambdaS3Role</lambdaRoleArn>
                        <s3Bucket>mys3bucket</s3Bucket>
                        <keyPrefix>my/awesome/prefix</keyPrefix>
                        <publish>${lambda.publish}</publish>
                        <forceUpdate>${lambda.forceUpdate}</forceUpdate>
                        <functionNameSuffix>${lambda.functionNameSuffix}</functionNameSuffix>
                        <environmentVariables>
                          <key0>value0</key0>
                        </environmentVariables>
                        <clientConfiguration>
                          <protocol>https</protocol>
                          <proxyHost>proxy-host.net</proxyHost>
                          <proxyPort>1234</proxyPort>
                        </clientConfiguration>
                        <lambdaFunctionsJSON>
                            [
                              {
                                "functionName": "my-function-name-0",
                                "description": "I am awesome function",
                                "handler": "no.flowlab.lambda0::test",
                                "timeout": 30,
                                "memorySize": 512,
                                "keepAlive": 15,
                                "triggers": [
                                  {
                                    "integration": "CloudWatch Events - Schedule",
                                    "ruleName": "every-minute",
                                    "ruleDescription": "foo bar",
                                    "scheduleExpression": "rate(1 minute)"
                                  },
                                  {
                                    "integration": "DynamoDB",
                                    "dynamoDBTable": "myTable",
                                    "batchSize": 100,
                                    "startingPosition": "TRIM_HORIZON"
                                  },
                                  {
                                    "integration": "Kinesis",
                                    "kinesisStream": "myStream",
                                    "batchSize": 100,
                                    "startingPosition": "TRIM_HORIZON"
                                  },
                                  {
                                    "integration": "SNS",
                                    "SNSTopic": "SNSTopic-1"
                                  },
                                  {
                                    "integration": "SNS",
                                    "SNSTopic": "SNSTopic-2"
                                  },
                                  {
                                    "integration": "Alexa Skills Kit"
                                    "alexaSkillId": "amzn1.ask.skill..."
                                  },
                                  {
                                    "integration": "Lex",
                                    "lexBotName": "BookCar"
                                  },
                                  {
                                    "integration": "SQS",
                                    "standardQueue": "queueName"
                                  }
                                ],
                                "environmentVariables": {
                                  "key1": "value1",
                                  "key2": "value2"
                                }
                              },
                              {
                                "functionName": "my-function-name-1",
                                "description": "I am awesome function too",
                                "handler": "no.flowlab.lambda1"
                              }
                            ]
                        </lambdaFunctionsJSON>
                    </configuration>
            </plugin>

            ...

        </project>
```
### A Note About Environment Variables
Environment variables set by this plugin respect the following hierarchy:
1. Variables set within the AWS Lambda Console.
2. Variables set within the Configuration block of the plugin (See above).
3. Variables set within the JSON lambda function descriptors (See above).
4. Pass through variables defined on the command line when deploying the function.

Variables defined at a higher level (top of the list above) may be overridden by those at a lower level.

### Credentials
Your AWS credentials may be set on the command line or in the plugin configuration. If `accessKey` and
`secretKey` are not explicitly defined, the plugin will look for them in your environment or in your
~/.aws/credentials file

IAM permissions required by this plugin:
* action `s3:GetObject` and `s3:PutObject` on resource `arn:aws:s3:::<s3Bucket>/*`
* action `s3:ListBucket` on resource `arn:aws:s3:::<s3Bucket>`
* action `s3:CreateBucket` if you want the plugin to create the S3 bucket you specify.
* action `lambda:CreateFunction`
* action `lambda:InvokeFunction`
* action `lambda:GetFunction`
* action `lambda:UpdateFunctionCode`
* action `lambda:UpdateFunctionConfiguration`
* action `lambda:ListAliases`
* action `lambda:GetPolicy` on resource: `arn:aws:lambda:<region>:<acount-number>:function:<function-name>`
* action `lambda:UpdateAlias` on resource: `arn:aws:lambda:<region>:<acount-number>:function:<function-name>`
* action `lambda:ListEventSourceMappings` on resource: *
* action `events:PutRule` on  resource `arn:aws:events:<region>:<acount-number>:rule/*`
* action `events:PutTargets` on  resource `arn:aws:events:<region>:<acount-number>:rule/*`
* action `events:ListRuleNamesByTarget` on  resource `arn:aws:events:<region>:<acount-number>:rule/*`
* action `events:DescribeRule` on  resource `arn:aws:events:<region>:<acount-number>:rule/KEEP-ALIVE-<function-name>`
* action `kinesis:GetRecords, GetShardIterator, DescribeStream, and ListStreams on Kinesis streams`
* action `sqs:GetQueueUrl, sqs:GetQueueAttributes on SQS`
* action `iam:PassRole` on  resource `<lambdaRoleArn>`
* action `SNS:ListSubscriptions` on  resource `arn:aws:events:<region>:<acount-number>:*`

### Developers
If you are interested in contributing to this project, please note that current development can be found in the SNAPSHOT branch of the coming release.  When making pull requests, please create them against this branch.

A test harness has been provided which can be run with `mvn test` Please use
this and feel free to add additional tests. Note that the basic-pom.xml file
requires you to add your role arn in order to work.  As such, basic-pom.xml
has been added to .gitignore so that you don't accidentally commit your role
to the file.  If you add more pom's as part of enhancing the test suite,
please remember to add them to .gitignore.

### Releases
2.3.5
* Ability to set skill Id for Alexa Skills Kit trigger. Thanks [mphartman1@gmail.com](mailto:mphartman1@gmail.com)
* Auto force update if version contains SNAPSHOT. Thanks [dimeo@elderresearch.com](mailto:dimeo@elderresearch.com)

2.3.4
* Resolves [Issue 117](https://github.com/SeanRoy/lambda-maven-plugin/issues/117) https://github.com/juger89
* Thanks [juger89@gmail.com](mailto:juger89@gmail.com)

2.3.3
* Added Support for SQS Trigger 

2.3.2
* Resolves [Issue 89](https://github.com/SeanRoy/lambda-maven-plugin/issues/89), allowing for encryption of environment variables defined on the command line.  See kmsEncryptionKey and encryptedPassThrough above.

2.3.1
* Resolves [Issue 87](https://github.com/SeanRoy/lambda-maven-plugin/issues/87), which was introduced in 2.3.0.

2.3.0
* Resolves [Issue 84](https://github.com/SeanRoy/lambda-maven-plugin/issues/84), Environment variables respect a hierarchy of definition and plugin will no longer wipe out existing variables

2.2.9
* Added ability to set http proxy on AWS clients. [Issue 39](https://github.com/SeanRoy/lambda-maven-plugin/issues/39)

2.2.8
* Added the ability to set an alias for the new function version, provided publish=true. Fixes [Issue 74](https://github.com/SeanRoy/lambda-maven-plugin/issues/74)

2.2.7
* Added SNS & Kinesis trigger orphan handling. This resolves [Issue 50](https://github.com/SeanRoy/lambda-maven-plugin/issues/50)

2.2.6
* Fixed another potential NPE, added orphan trigger cleanup for DynamoDB integrations.

2.2.5
* Fixed [Issue 77](https://github.com/SeanRoy/lambda-maven-plugin/issues/77)

2.2.4
* Smarter orphaned permission handling.

2.2.3
* Fixed [Issue 71](https://github.com/SeanRoy/lambda-maven-plugin/issues/71)
* Fixed [Issue 72](https://github.com/SeanRoy/lambda-maven-plugin/issues/72) By adding Lex integration

2.2.2
* Fixed [Issue 66](https://github.com/SeanRoy/lambda-maven-plugin/issues/66)
* Fixed sources of potential NPEs, thank [Jean Blanchard](https://github.com/jeanblanchard)

2.2.1
* Added passThrough environment variables feature, allowing you to pass environment variables from the command line.
* Added cleanup code to remove cloudwatch event rules that have become orphaned on when the function is being deleted.  More triggers will be added to the cleanup list in a later revision.

2.2.0
* Added Keep Alive functionality
* Fixed broken update schedule code.
* Added Kinesis trigger, thanks [Matt Van](https://github.com/mattvv)
* Deletion of triggers on lambda delete and the update code needs serious re-working.
* Need to work on cleaning up after orphaned resources.

2.1.7
* Fixed critical credentials bug introduced in 2.1.6.

2.1.6
* Removed some deprecated code
* functionNameSuffix is no longer automatically hyphenated.  If you want a hyphen, specify it in the functionNameSuffix directive.

2.1.5
* Add support for environment variables [Issue 48](https://github.com/SeanRoy/lambda-maven-plugin/issues/48)
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

2.1.4
* Fixed [Issue 46] (https://github.com/SeanRoy/lambda-maven-plugin/issues/46)
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

2.1.3
* Fixed [Issue 42] (https://github.com/SeanRoy/lambda-maven-plugin/issues/42)
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

2.1.2
* Added trigger to allow Alexa Skills Kit Integration.

2.1.1
* Remove deprecated `scheduledRules` and `topics` functionality
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

2.1.0
* Add support for triggers. Deprecated `scheduledRules` and `topics` as thouse have been moved to triggers
* Add support for DynamoDB stream. `lambdaRoleArn` requires AWSLambdaDynamoDBExecutionRole policy
* Update to AWS SDK 1.11.41
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

2.0.1
* Fixed [Issue 33] (https://github.com/SeanRoy/lambda-maven-plugin/pull/33) Thank Vũ Mạnh Tú.

2.0.0
* Add support for configuration many lambda functions in one deliverable, supports config in JSON, each lumbda function configuration can be fully customized
* Add support for version aliases when publish is activated
* Change defaults
* Fixed some mainor code smells
* Remove support for annotations
* Refactor code to java8
* Add publish flag, which controls Lambda versioning in AWS
* Force update support
* Add support for SNS topics
* Add support for scheduled rules, cron jobs which trigger lambda function
* Thanks [krzysztof@flowlab.no](mailto:krzysztof@flowlab.no)

1.1.6
* Removed debugging related code.

1.1.5
* Fixed bug where default value of functionNameSuffix evaluating to null instead of a blank string.

1.1.4
* Added functionNameSuffix optional property.

1.1.3
* Fixed [Issue 28] (https://github.com/SeanRoy/lambda-maven-plugin/issues/28)

1.1.2
* Fixed invalid dependency to lambda-maven-annotations

1.1.1
* Added support for Virtual Private Clouds. Thanks Jem Rayfield.
* Added ability to designate functions for deployment via LambduhFunction annotations. (Details coming soon).

1.1.0
* Added delete goal.

1.0.6
* Issue 19 Added test harness.
* Update function code if code or configuration has changed instead of
deleting and recreating every time.  Thanks Guillermo Menendez

1.0.5
* Accidental deployment of release.  Should be functionally equivalent to
1.0.4.

1.0.4
* Fixed issue 8
* No longer uploads function code to S3 when no changes have been made to speed up
  development cycle over slow connections.  Thanks Philip M. White.
* Fixed logging.

1.0.3
* Fixed a bug where getting a bucket fails if existing. Thanks buri17
* Fixed problem with region specification. Thanks buri17
* Adding ability to pull creds from the default provider. Thanks Chris Weiss

1.0.2
* Fixed PatternSyntaxException on windows https://github.com/SeanRoy/lambda-maven-plugin/issues/1
