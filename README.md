# Amazon Chime SMA ChatGPT IVR for Square Retail

## Background

This project is a [SIP Media Applications](https://docs.aws.amazon.com/chime-sdk/latest/ag/use-sip-apps.html) and makes use of the 
[Java Chime SMA Flow Library](https://github.com/docwho2/java-chime-voicesdk-sma) to deliver a ChatGPT voice bot IVR application.  
The IVR application is integrated with the [Square API](https://developer.squareup.com/us/en) to allow callers to ask questions about products 
and business hours, tranfer to employee cell phones, etc.



### High Level Components

![Architecture Diagram](assets/ChimeSMA-Square-IVR.png)

### Calling into the IVR Application

#### Twilio

[Twilio SIP Trunking](https://www.twilio.com/docs/sip-trunking) can be used to send calls into your SMA or the SIP carrier of your choice. For this demo, the Twilio number +1-320-495-2425 will be load balanced across regions. The first prompt in the demo announces the region, so you can observe that calling the above number will land you in either us-east-1 or us-west-2. When configuring the Twilio [Origination Settings](https://www.twilio.com/docs/sip-trunking#origination), you can make use of the "edge" setting to optimize the SIP traffic.

In this case, the first SIP URI references a [Voice Connector](https://docs.aws.amazon.com/chime-sdk/latest/ag/voice-connectors.html) in the us-east-1 region. By adding the "edge=ashburn" parameter in Twilio's configuration, the call will be egressed into AWS within us-east-1. The same applies for the "edge=umatilla" parameter, which is Twilio's edge in Oregon (us-west-2). It's recommended to minimize the traversal of traffic over the internet if possible.

![Twilio Origination Settings](assets/twilio.png)

#### Chime SDK Phone Number

After provisioning a [phone number in Chime](https://docs.aws.amazon.com/chime-sdk/latest/ag/provision-phone.html), you need to create a [SIP Rule](https://docs.aws.amazon.com/chime-sdk/latest/ag/understand-sip-data-models.html) for the phone number. Chime does not support load balancing, so you must set up an ordered priority. When you call +1-320-425-0645, you will always be routed to the SMA in the us-east-1 region. Only if that region or the Lambda associated with the SMA goes down will you fail over to the us-west-2 region.

Please note that Chime currently provides PSTN numbers only in the United States and not in all countries. If you are deploying in Europe or other regions, you will need to use a SIP carrier like Twilio, as mentioned above. I have tested these configurations in the Frankfurt and London regions without any issues. For a complete list of available regions for Chime SDK PSTN numbers, refer to the PSTN section in the [Available Regions](https://docs.aws.amazon.com/chime-sdk/latest/dg/sdk-available-regions.html) documentation.

![Chime Phone Targets](assets/chimephonenumber.png)

#### Asterisk PBX

For testing apps there certainly is no reason to incur PSTN charges, so I use an IP Phone connected to [Asterisk](https://www.asterisk.org) to place 
calls into SMA's.  Like Twilio above, in the [pjsip_wizzard.conf](https://wiki.asterisk.org/wiki/display/AST/PJSIP+Configuration+Wizard) you can create trunks 
for each region endpoint:

```
[aws-chime-east]
type=wizard
transport=transport-udp
remote_hosts=cze9epizslzqslzjpo58ff.voiceconnector.chime.aws
endpoint/disallow=all
endpoint/allow=ulaw
endpoint/direct_media=no
endpoint/dtmf_mode=auto
endpoint/rtp_symmetric=yes

[aws-chime-oregon]
type=wizard
transport=transport-udp
remote_hosts=dnpz57kzlmo6uvhb1anu3w.voiceconnector.chime.aws
endpoint/disallow=all
endpoint/allow=ulaw
endpoint/direct_media=no
endpoint/dtmf_mode=auto
endpoint/rtp_symmetric=yes
```

You can observe no less than 12 endpoints are ready to take your call in each region !!!

```
Asterisk*CLI> pjsip show endpoint aws-chime-oregon 

Endpoint:  aws-chime-oregon                                     Not in use    0 of inf
        Aor:  aws-chime-oregon                                   0
      Contact:  aws-chime-oregon/sip:dnpz57kzlmo6uvhb1anu3 228c75f425 Created       0.000
  Transport:  transport-udp             udp      0      0  0.0.0.0:5060
   Identify:  aws-chime-oregon-identify/aws-chime-oregon
        Match: 99.77.253.106/32
        Match: 99.77.253.110/32
        Match: 99.77.253.109/32
        Match: 99.77.253.104/32
        Match: 99.77.253.102/32
        Match: 99.77.253.107/32
        Match: 99.77.253.103/32
        Match: 99.77.253.105/32
        Match: 99.77.253.11/32
        Match: 99.77.253.0/32
        Match: 99.77.253.108/32
        Match: 99.77.253.100/32
```

In the [extensions.conf](https://wiki.asterisk.org/wiki/display/AST/Contexts%2C+Extensions%2C+and+Priorities) you configure a number you can dial 
to route to the trunks in question.  The number  +1-703-555-0122 is a Chime Call in number than can be used to route to SMA's.  This allows you to 
call into connectors and your SMA with a sip rule without provisioning a phone number at all !

- 290 will try us-east-1 first and if it fails, you hear a prompt (so you know the first region was down) and then it tries the next region
- 291 will call only us-east-1
- 292 will call only us-west-2

```
exten => 290,1,NoOP(Call to AWS Chime with ordered failover)
        same => n,Dial(PJSIP/+17035550122@aws-chime-east)
        same => n,Playback(sorry-youre-having-problems)
        same => n,Dial(PJSIP/+17035550122@aws-chime-oregon)

exten => 291,1,NoOP(Call to AWS Chime East)
        same => n,Dial(PJSIP/+17035550122@aws-chime-east)

exten => 292,1,NoOP(Call to AWS Chime Oregon)
        same => n,Dial(PJSIP/+17035550122@aws-chime-oregon)
```


### Starting the Flow



### Wrapping Up

This library, in combination with the CloudFormation template, demonstrates the following capabilities:

- Deployment of resilient multi-region voice applications.
- Creation of static prompts with Polly at deploy time to save costs on Polly usage.
- Deployment of static prompt files with the application.
- Easy integration with source control and pipeline deployment using "sam deploy".
- Programming flows in Java that are easy to understand compared to the SMA Event handling model.
- Extensibility of actions to keep flows concise and easy to understand.
- Utilization of Lambda SNAP Start to improve latency and performance.
- Locale support for multi-lingual applications.
- Leveraging the full power of Java for routing decisions and input/output to the actions.


These enhancements would further expand the functionality and flexibility of the library.

## Deploy the Project

The Serverless Application Model Command Line Interface (SAM CLI) is an extension of the AWS CLI that adds functionality for building and testing Lambda applications.  
Before proceeding, it is assumed you have valid AWS credentials setup with the AWS CLI and permissions to perform CloudFormation stack operations.

To use the SAM CLI, you need the following tools.

* SAM CLI - [Install the SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
* Java17 - [Install the Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html)
* Maven - [Install Maven](https://maven.apache.org/install.html)

If you have brew installed then
```bash
brew install aws-sam-cli
brew install corretto17
brew install maven
```

To build and deploy, run the following in your shell.  Note: you must edit the [samconfig.toml](samconfig.toml) and change the parameteres to 
taste before running the build like the SMA ID to ones that exist within that region.

```bash
git clone https://github.com/docwho2/java-squareup-chatgpt-ivr.git
cd java-squareup-chatgpt-ivr
./init.bash
sam build
sam deploy --config-env east
sam deploy --config-env west
```

You may find it easier to deploy in a [Cloud Shell](https://aws.amazon.com/cloudshell/).  Simply launch a Cloud Shell and install maven which also installs Java 17 by default, then proceed like above:

```bash
sudo yum install maven
git clone https://github.com/docwho2/java-squareup-chatgpt-ivr.git
cd java-squareup-chatgpt-ivr
./init.bash
sam build
sam deploy --config-env east
sam deploy --config-env west
```

The commands perform the follwoing operations:
- Clones the repository into your local directory
- Change directory into the cloned repository
- Set up some required components like the V4 Java Events library that is not published yet (this is a sub-module) and install the parent POM used by Lambda functions.
- Build the components that will be deployed by SAM
- Package and deploy the project to us-east-1
- Package and deploy the project to us-west-2

You will see the progress as the stack deploys.  As metntioned earlier, you will need to put your OpenAI and Square API Key into parameter store or the deploy will error, but it will give you an error message 
that tells you there is no value for "OPENAI_API_KEY" or "SQUARE_API_KEY" in the [Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html).



## Fetch, tail, and filter Lambda function logs

To simplify troubleshooting, SAM CLI has a command called `sam logs`. `sam logs` lets you fetch logs generated by the deployed Lambda functions from the command line. In addition to printing the logs on the terminal, this command has several nifty features to help you quickly see what's going on with the demo.


```bash
sam logs --tail
```

Example:
```
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.013000 RESTORE_START Runtime Version: java:11.v21	Runtime Version ARN: arn:aws:lambda:us-east-1::runtime:156ab0dc268a6b4a8dedcbcf0974795cafba2ee8760fe386062fffdbb887b971
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.376000 RESTORE_REPORT Restore Duration: 511.25 ms
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.379000 START RequestId: b771fecc-1b53-4faf-922d-1d74357b1676 Version: 56
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.565000 b771fecc-1b53-4faf-922d-1d74357b1676 DEBUG AbstractFlow:214 - SMARequest(schemaVersion=1.0, sequence=1, invocationEventType=NEW_INBOUND_CALL, callDetails=SMARequest.CallDetails(transactionId=6600de06-fc5a-4a57-8c11-420ccae6f93b, transactionAttributes=null, awsAccountId=364253738352, awsRegion=us-east-1, sipMediaApplicationId=cf3e17cd-f4e5-44c3-ab04-325e6b3a6709, participants=[SMARequest.Participant(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, to=+17035550122, from=+16128140714, direction=Inbound, startTime=2023-07-05T10:16:33.239Z, status=null)]), errorType=null, errorMessage=null, actionData=null)
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.566000 b771fecc-1b53-4faf-922d-1d74357b1676 DEBUG AbstractFlow:219 - New Inbound Call, starting flow
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.567000 b771fecc-1b53-4faf-922d-1d74357b1676 INFO  AbstractFlow:149 - Adding action PlayAudio key=[us-east-1-welcome.wav] bucket=[chime-voicesdk-sma-promptbucket-1p1tvnc4izve]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.567000 b771fecc-1b53-4faf-922d-1d74357b1676 INFO  AbstractFlow:157 - Chaining action PlayAudioAndGetDigits [^\d{1}$]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.595000 b771fecc-1b53-4faf-922d-1d74357b1676 INFO  AbstractFlow:238 - New Call Handler Code Here
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.682000 b771fecc-1b53-4faf-922d-1d74357b1676 DEBUG AbstractFlow:314 - {"SchemaVersion":"1.0","Actions":[{"Type":"PlayAudio","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1","ParticipantTag":"LEG-A","AudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"us-east-1-welcome.wav"}}},{"Type":"PlayAudioAndGetDigits","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1","ParticipantTag":"LEG-A","InputDigitsRegex":"^\\d{1}$","AudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"main-menu-en-US.wav"},"FailureAudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"try-again-en-US.wav"},"MinNumberOfDigits":1,"MaxNumberOfDigits":1,"Repeat":2,"RepeatDurationInMilliseconds":3000}}],"TransactionAttributes":{"CurrentActionId":"6","locale":"en-US","CurrentActionIdList":"18,6"}}
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.686000 END RequestId: b771fecc-1b53-4faf-922d-1d74357b1676
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:34.686000 REPORT RequestId: b771fecc-1b53-4faf-922d-1d74357b1676	Duration: 306.34 ms	Billed Duration: 610 ms	Memory Size: 3009 MB	Max Memory Used: 155 MB	Restore Duration: 511.25 ms	Billed Restore Duration: 303 ms	
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:43.928000 START RequestId: 7667964c-05ac-4891-b28c-56f1282ebc1b Version: 56
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.025000 7667964c-05ac-4891-b28c-56f1282ebc1b DEBUG AbstractFlow:214 - SMARequest(schemaVersion=1.0, sequence=2, invocationEventType=ACTION_SUCCESSFUL, callDetails=SMARequest.CallDetails(transactionId=6600de06-fc5a-4a57-8c11-420ccae6f93b, transactionAttributes={CurrentActionId=6, locale=en-US, CurrentActionIdList=18,6}, awsAccountId=364253738352, awsRegion=us-east-1, sipMediaApplicationId=cf3e17cd-f4e5-44c3-ab04-325e6b3a6709, participants=[SMARequest.Participant(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, to=+17035550122, from=+16128140714, direction=Inbound, startTime=2023-07-05T10:16:33.239Z, status=Connected)]), errorType=null, errorMessage=null, actionData=ResponsePlayAudioAndGetDigits(type=PlayAudioAndGetDigits, parameters=ResponsePlayAudioAndGetDigits.Parameters(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, inputDigitsRegex=^\d{1}$, audioSource=ResponsePlayAudio.AudioSource(type=S3, bucketName=chime-voicesdk-sma-promptbucket-1p1tvnc4izve, key=main-menu-en-US.wav), failureAudioSource=ResponsePlayAudio.AudioSource(type=S3, bucketName=chime-voicesdk-sma-promptbucket-1p1tvnc4izve, key=try-again-en-US.wav), minNumberOfDigits=1, maxNumberOfDigits=1, terminatorDigits=null, inBetweenDigitsDurationInMilliseconds=3000, repeat=2, repeatDurationInMilliseconds=3000), receivedDigits=1, errorType=null, errorMessage=null))
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.029000 7667964c-05ac-4891-b28c-56f1282ebc1b DEBUG AbstractFlow:207 - Current Action is PlayAudioAndGetDigits [^\d{1}$] with ID 6
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.030000 7667964c-05ac-4891-b28c-56f1282ebc1b DEBUG Action:180 - This Action has a locale set to en_US
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.031000 7667964c-05ac-4891-b28c-56f1282ebc1b INFO  AbstractFlow:149 - Adding action StartBotConversation desc=[ChatGPT English] da=[ElicitIntent] content=[What can Chat GPT help you with?]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.045000 7667964c-05ac-4891-b28c-56f1282ebc1b INFO  AbstractFlow:340 - Moving to next action: StartBotConversation desc=[ChatGPT English] da=[ElicitIntent] content=[What can Chat GPT help you with?]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.065000 7667964c-05ac-4891-b28c-56f1282ebc1b DEBUG AbstractFlow:314 - {"SchemaVersion":"1.0","Actions":[{"Type":"StartBotConversation","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1","BotAliasArn":"arn:aws:lex:us-east-1:364253738352:bot-alias/GDGCNIR2DC/NMJJX2WV6A","LocaleId":"en_US","Configuration":{"SessionState":{"DialogAction":{"Type":"ElicitIntent"}},"WelcomeMessages":[{"Content":"What can Chat GPT help you with?","ContentType":"PlainText"}]}}}],"TransactionAttributes":{"CurrentActionId":"4","locale":"en-US","CurrentActionIdList":"4"}}
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.067000 END RequestId: 7667964c-05ac-4891-b28c-56f1282ebc1b
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:44.067000 REPORT RequestId: 7667964c-05ac-4891-b28c-56f1282ebc1b	Duration: 138.27 ms	Billed Duration: 139 ms	Memory Size: 3009 MB	Max Memory Used: 159 MB	
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:55.980000 START RequestId: c5617d6b-3eff-409b-bff8-7f6ed83c319a Version: 56
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.025000 c5617d6b-3eff-409b-bff8-7f6ed83c319a DEBUG AbstractFlow:214 - SMARequest(schemaVersion=1.0, sequence=3, invocationEventType=ACTION_SUCCESSFUL, callDetails=SMARequest.CallDetails(transactionId=6600de06-fc5a-4a57-8c11-420ccae6f93b, transactionAttributes={CurrentActionId=4, locale=en-US, CurrentActionIdList=4}, awsAccountId=364253738352, awsRegion=us-east-1, sipMediaApplicationId=cf3e17cd-f4e5-44c3-ab04-325e6b3a6709, participants=[SMARequest.Participant(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, to=+17035550122, from=+16128140714, direction=Inbound, startTime=2023-07-05T10:16:33.239Z, status=Connected)]), errorType=null, errorMessage=null, actionData=ActionDataStartBotConversation(callId=null, type=StartBotConversation, parameters=ResponseStartBotConversation.Parameters(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, botAliasArn=arn:aws:lex:us-east-1:364253738352:bot-alias/GDGCNIR2DC/NMJJX2WV6A, localeId=en_US, configuration=ResponseStartBotConversation.Configuration(sessionState=ResponseStartBotConversation.SessionState(sessionAttributes=null, dialogAction=ResponseStartBotConversation.DialogAction(type=ElicitIntent), intent=null), welcomeMessages=[ResponseStartBotConversation.WelcomeMessage(content=What can Chat GPT help you with?, contentType=PlainText)])), intentResult=ActionDataStartBotConversation.IntentResult(sessionId=6cbd7153-b1cd-48b1-8598-9687f6903db1, sessionState=ResponseStartBotConversation.SessionState(sessionAttributes={}, dialogAction=null, intent=ResponseStartBotConversation.Intent(name=Quit, Slots={}, state=ReadyForFulfillment, confirmationState=None)), interpretations=[ActionDataStartBotConversation.Interpretation(intent=ResponseStartBotConversation.Intent(name=Quit, Slots={}, state=ReadyForFulfillment, confirmationState=None), nluConfidence=ActionDataStartBotConversation.NluConfidence(score=1.0)), ActionDataStartBotConversation.Interpretation(intent=ResponseStartBotConversation.Intent(name=FallbackIntent, Slots={}, state=null, confirmationState=null), nluConfidence=null), ActionDataStartBotConversation.Interpretation(intent=ResponseStartBotConversation.Intent(name=Transfer, Slots={}, state=null, confirmationState=null), nluConfidence=ActionDataStartBotConversation.NluConfidence(score=0.42))]), errorType=null, errorMessage=null))
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.026000 c5617d6b-3eff-409b-bff8-7f6ed83c319a DEBUG Action:180 - This Action has a locale set to en_US
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.026000 c5617d6b-3eff-409b-bff8-7f6ed83c319a DEBUG AbstractFlow:207 - Current Action is StartBotConversation desc=[ChatGPT English] da=[ElicitIntent] content=[What can Chat GPT help you with?] with ID 4
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.026000 c5617d6b-3eff-409b-bff8-7f6ed83c319a DEBUG Action:99 - Lex Bot has finished and Intent is Quit
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.027000 c5617d6b-3eff-409b-bff8-7f6ed83c319a INFO  AbstractFlow:149 - Adding action PlayAudioAndGetDigits [^\d{1}$]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.027000 c5617d6b-3eff-409b-bff8-7f6ed83c319a INFO  AbstractFlow:340 - Moving to next action: PlayAudioAndGetDigits [^\d{1}$]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.028000 c5617d6b-3eff-409b-bff8-7f6ed83c319a DEBUG AbstractFlow:314 - {"SchemaVersion":"1.0","Actions":[{"Type":"PlayAudioAndGetDigits","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1","ParticipantTag":"LEG-A","InputDigitsRegex":"^\\d{1}$","AudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"main-menu-en-US.wav"},"FailureAudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"try-again-en-US.wav"},"MinNumberOfDigits":1,"MaxNumberOfDigits":1,"Repeat":2,"RepeatDurationInMilliseconds":3000}}],"TransactionAttributes":{"CurrentActionId":"6","locale":"en-US","CurrentActionIdList":"6","LexLastMatchedIntent":"Quit"}}
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.031000 END RequestId: c5617d6b-3eff-409b-bff8-7f6ed83c319a
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:56.031000 REPORT RequestId: c5617d6b-3eff-409b-bff8-7f6ed83c319a	Duration: 50.76 ms	Billed Duration: 51 ms	Memory Size: 3009 MB	Max Memory Used: 159 MB	
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.051000 START RequestId: 09cea32b-12e6-4283-9092-15e3fd5eabf8 Version: 56
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.054000 09cea32b-12e6-4283-9092-15e3fd5eabf8 DEBUG AbstractFlow:214 - SMARequest(schemaVersion=1.0, sequence=4, invocationEventType=ACTION_SUCCESSFUL, callDetails=SMARequest.CallDetails(transactionId=6600de06-fc5a-4a57-8c11-420ccae6f93b, transactionAttributes={CurrentActionId=6, LexLastMatchedIntent=Quit, locale=en-US, CurrentActionIdList=6}, awsAccountId=364253738352, awsRegion=us-east-1, sipMediaApplicationId=cf3e17cd-f4e5-44c3-ab04-325e6b3a6709, participants=[SMARequest.Participant(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, to=+17035550122, from=+16128140714, direction=Inbound, startTime=2023-07-05T10:16:33.239Z, status=Connected)]), errorType=null, errorMessage=null, actionData=ResponsePlayAudioAndGetDigits(type=PlayAudioAndGetDigits, parameters=ResponsePlayAudioAndGetDigits.Parameters(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, inputDigitsRegex=^\d{1}$, audioSource=ResponsePlayAudio.AudioSource(type=S3, bucketName=chime-voicesdk-sma-promptbucket-1p1tvnc4izve, key=main-menu-en-US.wav), failureAudioSource=ResponsePlayAudio.AudioSource(type=S3, bucketName=chime-voicesdk-sma-promptbucket-1p1tvnc4izve, key=try-again-en-US.wav), minNumberOfDigits=1, maxNumberOfDigits=1, terminatorDigits=null, inBetweenDigitsDurationInMilliseconds=3000, repeat=2, repeatDurationInMilliseconds=3000), receivedDigits=8, errorType=null, errorMessage=null))
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.054000 09cea32b-12e6-4283-9092-15e3fd5eabf8 DEBUG AbstractFlow:207 - Current Action is PlayAudioAndGetDigits [^\d{1}$] with ID 6
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.054000 09cea32b-12e6-4283-9092-15e3fd5eabf8 INFO  AbstractFlow:149 - Adding action PlayAudio desc=[Say Goodbye] keyL=[goodbye] bucket=[chime-voicesdk-sma-promptbucket-1p1tvnc4izve]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.055000 09cea32b-12e6-4283-9092-15e3fd5eabf8 INFO  AbstractFlow:157 - Chaining action Hangup desc=[This is my last step]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.057000 09cea32b-12e6-4283-9092-15e3fd5eabf8 INFO  AbstractFlow:340 - Moving to next action: PlayAudio desc=[Say Goodbye] keyL=[goodbye] bucket=[chime-voicesdk-sma-promptbucket-1p1tvnc4izve]
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.060000 09cea32b-12e6-4283-9092-15e3fd5eabf8 DEBUG AbstractFlow:314 - {"SchemaVersion":"1.0","Actions":[{"Type":"PlayAudio","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1","ParticipantTag":"LEG-A","AudioSource":{"Type":"S3","BucketName":"chime-voicesdk-sma-promptbucket-1p1tvnc4izve","Key":"goodbye-en-US.wav"}}},{"Type":"Hangup","Parameters":{"CallId":"6cbd7153-b1cd-48b1-8598-9687f6903db1"}}],"TransactionAttributes":{"CurrentActionId":"1","LexLastMatchedIntent":"Quit","locale":"en-US","CurrentActionIdList":"2,1"}}
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.061000 END RequestId: 09cea32b-12e6-4283-9092-15e3fd5eabf8
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:16:58.061000 REPORT RequestId: 09cea32b-12e6-4283-9092-15e3fd5eabf8	Duration: 10.09 ms	Billed Duration: 11 ms	Memory Size: 3009 MB	Max Memory Used: 160 MB	
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.009000 START RequestId: 010f6002-d185-4e96-9971-5360a2c6aa72 Version: 56
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.014000 010f6002-d185-4e96-9971-5360a2c6aa72 DEBUG AbstractFlow:214 - SMARequest(schemaVersion=1.0, sequence=5, invocationEventType=HANGUP, callDetails=SMARequest.CallDetails(transactionId=6600de06-fc5a-4a57-8c11-420ccae6f93b, transactionAttributes={CurrentActionId=1, LexLastMatchedIntent=Quit, locale=en-US, CurrentActionIdList=2,1}, awsAccountId=364253738352, awsRegion=us-east-1, sipMediaApplicationId=cf3e17cd-f4e5-44c3-ab04-325e6b3a6709, participants=[SMARequest.Participant(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, to=+17035550122, from=+16128140714, direction=Inbound, startTime=2023-07-05T10:16:33.239Z, status=Disconnected)]), errorType=null, errorMessage=null, actionData=ResponseHangup(type=Hangup, parameters=ResponseHangup.Parameters(callId=6cbd7153-b1cd-48b1-8598-9687f6903db1, participantTag=LEG-A, sipResponseCode=null)))
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.015000 010f6002-d185-4e96-9971-5360a2c6aa72 DEBUG AbstractFlow:270 - Call Was disconnected by [Application], sending empty response
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.015000 010f6002-d185-4e96-9971-5360a2c6aa72 DEBUG AbstractFlow:207 - Current Action is Hangup desc=[This is my last step] with ID 1
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.015000 010f6002-d185-4e96-9971-5360a2c6aa72 INFO  AbstractFlow:243 - Hangup Handler Code Here
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.016000 010f6002-d185-4e96-9971-5360a2c6aa72 DEBUG AbstractFlow:314 - {"SchemaVersion":"1.0","Actions":[]}
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.017000 END RequestId: 010f6002-d185-4e96-9971-5360a2c6aa72
2023/07/05/[56]374cd7798d2a4e36a494a23d658b9741 2023-07-05T10:17:00.017000 REPORT RequestId: 010f6002-d185-4e96-9971-5360a2c6aa72	Duration: 8.31 ms	Billed Duration: 9 ms	Memory Size: 3009 MB	Max Memory Used: 160 MB	

^C CTRL+C received, cancelling...                                              
```

You can find more information and examples about filtering Lambda function logs in the [SAM CLI Documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-logging.html).


## Cleanup

To delete the demo, use the SAM CLI.

You can run the following:

```bash
sam delete --config-env east
sam delete --config-env west
```

## Sample Deploy Output
```
java-chime-voicesdk-sma$ sam deploy --config-env west

		Managed S3 bucket: aws-sam-cli-managed-default-samclisourcebucket-13jnbeuzx2
		A different default S3 bucket can be set in samconfig.toml
		Or by specifying --s3-bucket explicitly.
	Uploading to chime-voicesdk-sma/0934badf714b3b6d4be6b4716f73d980  17401808 / 17401808  (100.00%)
File with same data already exists at chime-voicesdk-sma/0934badf714b3b6d4be6b4716f73d980, skipping upload                                        
	Uploading to chime-voicesdk-sma/3dd27d8c98d6bee8bdcca98c26123f26  14703860 / 14703860  (100.00%)
	Uploading to chime-voicesdk-sma/2eac5038f204c6de13a836fb2da6efb9  22977524 / 22977524  (100.00%)

	Deploying with following values
	===============================
	Stack name                   : chime-voicesdk-sma
	Region                       : us-west-2
	Confirm changeset            : False
	Disable rollback             : False
	Deployment s3 bucket         : aws-sam-cli-managed-default-samclisourcebucket-13jnbug4euzx2
	Capabilities                 : ["CAPABILITY_IAM"]
	Parameter overrides          : {"SMAID": "f6fb2553-e7e0-4900-866b-1b51b91f575a", "CONNECTID": "e8fac445-d291-407e-8fd7-c6296395c2ab"}
	Signing Profiles             : {}

Initiating deployment
=====================

	Uploading to chime-voicesdk-sma/93e0edbe536d96ab0deced939610d637.template  22349 / 22349  (100.00%)


Waiting for changeset to be created..

CloudFormation stack changeset
---------------------------------------------------------------------------------------------------------------------------------------------
Operation                           LogicalResourceId                   ResourceType                        Replacement                       
---------------------------------------------------------------------------------------------------------------------------------------------
+ Add                               BotAliasGPT                         AWS::Lex::BotAlias                  N/A                               
+ Add                               BotRuntimeRole                      AWS::IAM::Role                      N/A                               
+ Add                               BotVersionGPT                       AWS::Lex::BotVersion                N/A                               
+ Add                               ChatGPTAliasSNAPSTART               AWS::Lambda::Alias                  N/A                               
+ Add                               ChatGPTRole                         AWS::IAM::Role                      N/A                               
+ Add                               ChatGPTVersion3d508bab8c            AWS::Lambda::Version                N/A                               
+ Add                               ChatGPT                             AWS::Lambda::Function               N/A                               
+ Add                               ChimeCallLexGPT                     AWS::Lex::ResourcePolicy            N/A                               
+ Add                               ChimePolicy                         AWS::IAM::ManagedPolicy             N/A                               
+ Add                               ChimeSMAPerm                        AWS::Lambda::Permission             N/A                               
+ Add                               ChimeSMARole                        AWS::IAM::Role                      N/A                               
+ Add                               ChimeSMA                            AWS::Lambda::Function               N/A                               
+ Add                               GoodbyePromptEN                     Custom::PromptCreator               N/A                               
+ Add                               GoodbyePromptES                     Custom::PromptCreator               N/A                               
+ Add                               LexBotGPT                           AWS::Lex::Bot                       N/A                               
+ Add                               LexToChatGPTPerm                    AWS::Lambda::Permission             N/A                               
+ Add                               LexToChatGPTSnapPerm                AWS::Lambda::Permission             N/A                               
+ Add                               MainMenuEN                          Custom::PromptCreator               N/A                               
+ Add                               MainMenuES                          Custom::PromptCreator               N/A                               
+ Add                               MainPromptEast                      Custom::PromptCreator               N/A                               
+ Add                               MainPromptWest                      Custom::PromptCreator               N/A                               
+ Add                               PromptBucketPolicy                  AWS::S3::BucketPolicy               N/A                               
+ Add                               PromptBucket                        AWS::S3::Bucket                     N/A                               
+ Add                               PromptCopierRole                    AWS::IAM::Role                      N/A                               
+ Add                               PromptCopier                        AWS::Lambda::Function               N/A                               
+ Add                               PromptCreatorRole                   AWS::IAM::Role                      N/A                               
+ Add                               PromptCreator                       AWS::Lambda::Function               N/A                               
+ Add                               RecordBucketPolicy                  AWS::S3::BucketPolicy               N/A                               
+ Add                               RecordBucket                        AWS::S3::Bucket                     N/A                               
+ Add                               SessionTable                        AWS::DynamoDB::Table                N/A                               
+ Add                               StaticPrompts                       Custom::PromptCopier                N/A                               
+ Add                               TansferPromptEN                     Custom::PromptCreator               N/A                               
+ Add                               TansferPromptES                     Custom::PromptCreator               N/A                               
+ Add                               TransferCallConnectIntegration      AWS::Connect::IntegrationAssociat   N/A                               
                                                                        ion                                                                   
+ Add                               TransferCallRole                    AWS::IAM::Role                      N/A                               
+ Add                               TransferCall                        AWS::Lambda::Function               N/A                               
+ Add                               TryAgainEN                          Custom::PromptCreator               N/A                               
+ Add                               TryAgainES                          Custom::PromptCreator               N/A                               
---------------------------------------------------------------------------------------------------------------------------------------------


Changeset created successfully. arn:aws:cloudformation:us-west-2:changeSet/samcli-deploy1688419429/b28c26d0-12a8-4efc-a9af-aa49d9c404c9


2023-07-03 16:24:07 - Waiting for stack create/update to complete

CloudFormation events from stack operations (refresh every 5.0 seconds)
---------------------------------------------------------------------------------------------------------------------------------------------
ResourceStatus                      ResourceType                        LogicalResourceId                   ResourceStatusReason              
---------------------------------------------------------------------------------------------------------------------------------------------
CREATE_IN_PROGRESS                  AWS::IAM::Role                      BotRuntimeRole                      -                                 
CREATE_IN_PROGRESS                  AWS::DynamoDB::Table                SessionTable                        -                                 
CREATE_IN_PROGRESS                  AWS::S3::Bucket                     RecordBucket                        -                                 
CREATE_IN_PROGRESS                  AWS::IAM::ManagedPolicy             ChimePolicy                         -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      ChimeSMARole                        -                                 
CREATE_IN_PROGRESS                  AWS::S3::Bucket                     PromptBucket                        -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      BotRuntimeRole                      Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::IAM::ManagedPolicy             ChimePolicy                         Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::IAM::Role                      ChimeSMARole                        Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::S3::Bucket                     RecordBucket                        Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::S3::Bucket                     PromptBucket                        Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::DynamoDB::Table                SessionTable                        Resource creation Initiated       
CREATE_COMPLETE                     AWS::DynamoDB::Table                SessionTable                        -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      ChatGPTRole                         -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      ChatGPTRole                         Resource creation Initiated       
CREATE_COMPLETE                     AWS::IAM::ManagedPolicy             ChimePolicy                         -                                 
CREATE_COMPLETE                     AWS::IAM::Role                      BotRuntimeRole                      -                                 
CREATE_COMPLETE                     AWS::IAM::Role                      ChimeSMARole                        -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      TransferCallRole                    -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      TransferCallRole                    Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lex::Bot                       LexBotGPT                           -                                 
CREATE_IN_PROGRESS                  AWS::Lex::Bot                       LexBotGPT                           Resource creation Initiated       
CREATE_COMPLETE                     AWS::S3::Bucket                     RecordBucket                        -                                 
CREATE_COMPLETE                     AWS::S3::Bucket                     PromptBucket                        -                                 
CREATE_IN_PROGRESS                  AWS::S3::BucketPolicy               RecordBucketPolicy                  -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      PromptCreatorRole                   -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      PromptCopierRole                    -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      PromptCreatorRole                   Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::S3::BucketPolicy               PromptBucketPolicy                  -                                 
CREATE_IN_PROGRESS                  AWS::IAM::Role                      PromptCopierRole                    Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::S3::BucketPolicy               RecordBucketPolicy                  Resource creation Initiated       
CREATE_COMPLETE                     AWS::S3::BucketPolicy               RecordBucketPolicy                  -                                 
CREATE_IN_PROGRESS                  AWS::S3::BucketPolicy               PromptBucketPolicy                  Resource creation Initiated       
CREATE_COMPLETE                     AWS::S3::BucketPolicy               PromptBucketPolicy                  -                                 
CREATE_COMPLETE                     AWS::IAM::Role                      ChatGPTRole                         -                                 
CREATE_COMPLETE                     AWS::IAM::Role                      TransferCallRole                    -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               ChatGPT                             -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               ChatGPT                             Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lambda::Function               TransferCall                        -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               TransferCall                        Resource creation Initiated       
CREATE_COMPLETE                     AWS::IAM::Role                      PromptCreatorRole                   -                                 
CREATE_COMPLETE                     AWS::IAM::Role                      PromptCopierRole                    -                                 
CREATE_COMPLETE                     AWS::Lambda::Function               ChatGPT                             -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               PromptCopier                        -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               PromptCreator                       -                                 
CREATE_COMPLETE                     AWS::Lambda::Function               TransferCall                        -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Version                ChatGPTVersion3d508bab8c            -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             LexToChatGPTPerm                    -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               PromptCopier                        Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             LexToChatGPTPerm                    Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lambda::Function               PromptCreator                       Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lambda::Version                ChatGPTVersion3d508bab8c            Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Connect::IntegrationAssociat   TransferCallConnectIntegration      -                                 
                                    ion                                                                                                       
CREATE_IN_PROGRESS                  AWS::Connect::IntegrationAssociat   TransferCallConnectIntegration      Resource creation Initiated       
                                    ion                                                                                                       
CREATE_COMPLETE                     AWS::Connect::IntegrationAssociat   TransferCallConnectIntegration      -                                 
                                    ion                                                                                                       
CREATE_COMPLETE                     AWS::Lex::Bot                       LexBotGPT                           -                                 
CREATE_COMPLETE                     AWS::Lambda::Function               PromptCopier                        -                                 
CREATE_COMPLETE                     AWS::Lambda::Function               PromptCreator                       -                                 
CREATE_IN_PROGRESS                  AWS::Lex::BotVersion                BotVersionGPT                       -                                 
CREATE_IN_PROGRESS                  Custom::PromptCopier                StaticPrompts                       -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TansferPromptEN                     -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TryAgainEN                          -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainMenuEN                          -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TansferPromptES                     -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainPromptWest                      -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TryAgainES                          -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainMenuES                          -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               GoodbyePromptEN                     -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainPromptEast                      -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               GoodbyePromptES                     -                                 
CREATE_IN_PROGRESS                  AWS::Lex::BotVersion                BotVersionGPT                       Resource creation Initiated       
CREATE_COMPLETE                     AWS::Lambda::Permission             LexToChatGPTPerm                    -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TryAgainEN                          Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               GoodbyePromptEN                     Resource creation Initiated       
CREATE_COMPLETE                     Custom::PromptCreator               TryAgainEN                          -                                 
CREATE_COMPLETE                     Custom::PromptCreator               GoodbyePromptEN                     -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               TansferPromptEN                     Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               TansferPromptES                     Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainPromptWest                      Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainMenuEN                          Resource creation Initiated       
CREATE_COMPLETE                     Custom::PromptCreator               TansferPromptEN                     -                                 
CREATE_COMPLETE                     Custom::PromptCreator               TansferPromptES                     -                                 
CREATE_COMPLETE                     Custom::PromptCreator               MainPromptWest                      -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainMenuES                          Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               TryAgainES                          Resource creation Initiated       
CREATE_COMPLETE                     Custom::PromptCreator               MainMenuEN                          -                                 
CREATE_IN_PROGRESS                  Custom::PromptCreator               MainPromptEast                      Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCopier                StaticPrompts                       Resource creation Initiated       
CREATE_IN_PROGRESS                  Custom::PromptCreator               GoodbyePromptES                     Resource creation Initiated       
CREATE_COMPLETE                     Custom::PromptCreator               MainMenuES                          -                                 
CREATE_COMPLETE                     Custom::PromptCreator               TryAgainES                          -                                 
CREATE_COMPLETE                     Custom::PromptCreator               MainPromptEast                      -                                 
CREATE_COMPLETE                     Custom::PromptCopier                StaticPrompts                       -                                 
CREATE_COMPLETE                     Custom::PromptCreator               GoodbyePromptES                     -                                 
CREATE_COMPLETE                     AWS::Lex::BotVersion                BotVersionGPT                       -                                 
CREATE_COMPLETE                     AWS::Lambda::Version                ChatGPTVersion3d508bab8c            -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Alias                  ChatGPTAliasSNAPSTART               -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Alias                  ChatGPTAliasSNAPSTART               Resource creation Initiated       
CREATE_COMPLETE                     AWS::Lambda::Alias                  ChatGPTAliasSNAPSTART               -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             LexToChatGPTSnapPerm                -                                 
CREATE_IN_PROGRESS                  AWS::Lex::BotAlias                  BotAliasGPT                         -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             LexToChatGPTSnapPerm                Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lex::BotAlias                  BotAliasGPT                         Resource creation Initiated       
CREATE_COMPLETE                     AWS::Lex::BotAlias                  BotAliasGPT                         -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               ChimeSMA                            -                                 
CREATE_IN_PROGRESS                  AWS::Lex::ResourcePolicy            ChimeCallLexGPT                     -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Function               ChimeSMA                            Resource creation Initiated       
CREATE_IN_PROGRESS                  AWS::Lex::ResourcePolicy            ChimeCallLexGPT                     Resource creation Initiated       
CREATE_COMPLETE                     AWS::Lex::ResourcePolicy            ChimeCallLexGPT                     -                                 
CREATE_COMPLETE                     AWS::Lambda::Permission             LexToChatGPTSnapPerm                -                                 
CREATE_COMPLETE                     AWS::Lambda::Function               ChimeSMA                            -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             ChimeSMAPerm                        -                                 
CREATE_IN_PROGRESS                  AWS::Lambda::Permission             ChimeSMAPerm                        Resource creation Initiated       
CREATE_COMPLETE                     AWS::Lambda::Permission             ChimeSMAPerm                        -                                 
CREATE_COMPLETE                     AWS::CloudFormation::Stack          chime-voicesdk-sma                  -                                 
---------------------------------------------------------------------------------------------------------------------------------------------


Successfully created/updated stack - chime-voicesdk-sma in us-west-2


```
