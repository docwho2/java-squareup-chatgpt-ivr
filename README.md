# Amazon Chime SMA ChatGPT IVR for Square Retail

## Background

This project is a [SIP Media Application](https://docs.aws.amazon.com/chime-sdk/latest/ag/use-sip-apps.html) and makes use of the 
[Java Chime SMA Flow Library](https://github.com/docwho2/java-chime-voicesdk-sma) to deliver a [ChatGPT](https://openai.com/chatgpt) voice bot IVR application. The IVR application is integrated with the [Square API](https://developer.squareup.com/us/en) to allow callers to ask questions about products 
and business hours, transfer to employee cell phones, etc.

## Use Case

[Copper Fox Gifts](https://www.copperfoxgifts.com) is a retail store located in a small town in MN. The goal is to handle the majority of calls without human intervention. Here's a breakdown of the types of calls they receive:

- **About 50% of the calls**: "Are you open now?" This is a typical question in resort-type towns where store hours change seasonally and frequently. Many visitors prefer to call ahead to ensure the store is open before they head out.
  - A common follow-up to this is: "What are your hours today?"
  
- **45% of the calls**: "Do you have XYZ product?" Queries range from mittens, hats, and gummy bears to shorts, candles, and more.

- **The remaining calls**: These are primarily from vendors who are coordinating with a specific individual at the store and wish to speak with them directly.


## Solution Summary

The goal is to introduce a "Store Virtual Assistant" powered by [OpenAI ChatGPT](https://openai.com/chatgpt) that can not only answer store-specific queries but also address any general questions the caller might have.
- Utilize [ChatGPT Function Calls](https://platform.openai.com/docs/guides/gpt/function-calling) to facilitate Square API calls, enabling access to inventory, employee details, and store hours.
  - Further leverage function calls so that the model can determine when a call should be transferred or concluded.
- Employ strategic prompting to prime the model with pertinent store information and to guide interactions with callers.
- Ensure a robust and dependable solution that is deployed across multiple regions within AWS and is entirely cloud-based.
- CI/CD [GitHub Workflow](https://docs.github.com/en/actions/using-workflows/about-workflows) that deploys/updates two regions in parallel.

**Features:**
- Callers are consistently greeted and informed about the store's operational status (open or closed).
- Store hours are ascertained in real-time via a Square API call.
- Product category and individual item searches are also driven by Square API calls.
  - Callers can inquire about product categories or specific items in stock.
- Engaging with store employees or the primary store line.
  - Callers can request to connect with specific employees, with the information sourced from a Square API call (Team Member list).
  - If the caller simply wishes to speak to a representative, the model is preloaded with a default number to redirect the call.
    - During transfers to the main line, this process is optimized to use SIP directly connecting to the store's [Asterisk PBX](https://www.asterisk.org).
- You can call +1 (320) 425-0645 to try it, please don't ask to speak with someone as this is a real store with real people working in it.  This is deployed in production via the [workflow](.github/workflows/deploy.yml) and answers all calls for the store.

## High Level Components

![Architecture Diagram](assets/ChimeSMA-Square-IVR.png)

## Call Flow Details

### SMA Controller

The [ChimeSMA](ChimeSMA/src/main/java/cloud/cleo/chimesma/squareup/ChimeSMA.java) controller controls the call at a high level.  Callers are greeted and told whether the store is open or closed.

```Java
protected Action getInitialAction() {

        // Play open or closed prompt based on Square Hours  
        final var openClosed = PlayAudioAction.builder()
                .withKeyF(f -> squareHours.isOpen() ? "open.wav" : "closed.wav") // This is always in english
                .withNextAction(MAIN_MENU)
                .withErrorAction(MAIN_MENU)
                .build();

        // Start with a welcome message
        final var welcome = PlayAudioAction.builder()
                .withKey("welcome.wav") // This is always in english
                .withNextAction(openClosed)
                .withErrorAction(openClosed)
                .build();

        return welcome;
    }
```

Control is then handed off to a Lex Bot which is backed by ChatGPT to handle the interaction until a terminating event happens.

```Java
final var lexBotEN = StartBotConversationAction.builder()
                .withDescription("ChatGPT English")
                .withLocale(english)
                .withContent("You can ask about our products, hours, location, or speak to one of our team members. Tell us how we can help today?")
                .build();
```

If ChatGPT determines the call needs to be transferred or ended, that intent is returned and the SMA Controller transfers or ends the call.

```Java
Function<StartBotConversationAction, Action> botNextAction = (a) -> {
            return switch (a.getIntentName()) {
                case "Transfer" -> {
                    final var attrs = a.getActionData().getIntentResult().getSessionState().getSessionAttributes();
                    final var botResponse = attrs.get("botResponse");
                    final var phone = attrs.get("transferNumber");
                    final var transfer = CallAndBridgeAction.builder()
                            .withDescription("Send Call to Team Member")
                            .withRingbackToneKey("ringing.wav")
                            .withUri(phone)
                            .build();
                    if (phone.equals(MAIN_NUMBER) && !VC_ARN.equalsIgnoreCase("PSTN")) {
                        // We are transferring to main number, so use SIP by sending call to Voice Connector
                        transfer.setArn(VC_ARN);
                        transfer.setDescription("Send Call to Main Number via SIP");
                    }
                    // If we have a GPT response for the transfer, play that before transferring
                    if (botResponse != null) {
                        yield SpeakAction.builder()
                        .withText(botResponse)
                        .withNextAction(transfer)
                        .build();
                    } else {
                        yield transfer;
                    }
                }
                case "Quit" ->
                    goodbye;
                default ->
                    SpeakAction.builder()
                    .withText("A system error has occured, please call back and try again")
                    .withNextAction(hangup)
                    .build();
            }; 
        };
```

### ChatGPT Fullfillment Lambda

The [ChatGPTLambda](ChatGPT/src/main/java/cloud/cleo/squareup/ChatGPTLambda.java) and [Functions](ChatGPT/src/main/java/cloud/cleo/squareup/functions) allow the caller to interface with ChatGPT and provide answers based on realtime data from Square API's.  The model is initialized when a new [Session](ChatGPT/src/main/java/cloud/cleo/squareup/ChatGPTSessionState.java) is started.

```Java
         // General Prompting
        sb.append("Please be a helpfull assistant for a retail store named \"Copper Fox Gifts\", which has clothing items, home decor, gifts of all kinds, speciality foods, and much more.  ");
        sb.append("The store is located at 160 Main Street, Wahkon Minnesota, near Lake Mille Lacs.  ");
        sb.append("Do not respond with the whole employee list.  You may confirm the existance of an employee and give the full name.  ");

        // Mode specific prompting
        switch (inputMode) {
            case TEXT -> {
                sb.append("I am interacting via SMS.  Please keep answers very short and concise, preferably under 180 characters.  ");
                sb.append("To interact with an employee suggest the person call this number instead of texting and ask to speak to that person.  ");
            }
            case SPEECH, DTMF -> {
                sb.append("I am interacting with speech via a telephone interface.  please keep answers short and concise.  ");
                
                // Hangup
                sb.append("When the caller indicates they are done with the conversation, execute the ").append(HANGUP_FUNCTION_NAME).append(" function.  ");
                
                // Transferring
                sb.append("To transfer or speak with a employee that has a phone number, execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                sb.append("If the caller wants to just speak to a person or leave a voicemail, execute ").append(TRANSFER_FUNCTION_NAME).append(" with ").append(System.getenv("MAIN_NUMBER")).append(" which rings the main phone in the store.  ");
                sb.append("Do not provide employee phone numbers, you can use the phone numbers to execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                
                // Only recommend resturants for call ins, not SMS
                sb.append("Muggs of Mille Lacs is a great resturant next door that serves some on the best burgers in the lake area and has a large selection draft beers and great pub fare.  ");
            }
        }
```

### Example ChatGPT Function

The [Store Hours](ChatGPT/src/main/java/cloud/cleo/squareup/functions/SquareHours.java) function is an example of extending the model to provide realtime information.  The executor for the function is rather simple.

```Java
@Override
public String getDescription() {
        return "Return the open store hours by day of week, any day of week not returned means the store is closed that day.";
    }

public Function<Request, Object> getExecutor() {
        return (var r) -> {
            try {
                return client.getLocationsApi()
                        .retrieveLocation(System.getenv("SQUARE_LOCATION_ID"))
                        .getLocation().getBusinessHours();
            } catch (Exception ex) {
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            } 
        };
    }
```

This API call to Square returns a JSON structure that is returned directly to ChatGPT for it to interpret.

```Json
    {
      "periods": [
        {
          "day_of_week": "SUN",
          "start_local_time": "11:00:00",
          "end_local_time": "15:00:00"
        },
        {
          "day_of_week": "FRI",
          "start_local_time": "10:00:00",
          "end_local_time": "17:00:00"
        },
        {
          "day_of_week": "SAT",
          "start_local_time": "10:00:00",
          "end_local_time": "17:00:00"
        }
      ]
    }
```

Depending on how the caller asks about hours, the model uses the data to answer questions:

- Are you open next Thursday?
    - No, we are closed on Thursdays (sometimes it will then speak all the open hours)
- What are your hours?
    - Speaks out the hours for each day in order (SUN, FRI, SAT)
    - Sometimes it sumarizes and says Saturday and Sunday from 10 to 5 and Sunday from 11 to 3

## Deploy the Project

This project uses both AWS SAM and AWS CDK to deploy resources.  Because Chime Voice SDK resources are not included in CloudFormation, they must be provisioned using AWS API calls.  Custom resources must be created to solve this problem.  This project includes and makes use of [Chime Voice SDK Provisioning Project](https://github.com/docwho2/java-chime-voice-sdk-cdk).

* CDK code deploys Chime resources like the SIP media application (SMA), Voice Connector, and SIP rules to enable SIP connectivity.
* SAM deploys the actual Lambda's (ChatGPT and SMA), Lex Bot, S3 Prompt Bucket, Prompts, etc.

Some of the prerequisites to fully utilize the project are API keys for both OpenAI and Square.  If you don't care about testing the Square retail features and only want to interfact with ChatGPT, then you can skip any of the Square values and the project will still work.

* [Obtain OpenAI API key](https://platform.openai.com/docs/quickstart?context=curl)
* [Obtain Square API Key](https://developer.squareup.com/docs/build-basics/access-tokens)
  * [Obtain Square location ID](https://developer.squareup.com/blog/see-your-location-id-without-the-api-call/)

### Forking repository and utlizing the GitHub Workflow

The [GitHub Workflow](.github/workflows/deploy.yml) included in the repository can be used to a create a full CI/CD pipeline as changes are comitted to the main branch.

To allow the workflow to operate on your AWS environment, you can use several methods, but in this case we are using the recommended [OIDC method](https://github.com/aws-actions/configure-aws-credentials#OIDC) that requires some setup inside your account.  The workflow uses this to setup Credentials:

```yaml
- name: Setup AWS Credentials
      id: aws-creds
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-region: ${{ matrix.region }}
        # The full role ARN if you are using OIDC
        # https://github.com/aws-actions/configure-aws-credentials#oidc
        role-to-assume: ${{ secrets.AWS_ROLE_TO_ASSUME }}
        # Set up the below secrets if you are not using OIDC and want to use regular keys (best practive is to use just role above with OIDC provider)
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        mask-aws-account-id: true
```

You will need to [create secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions#creating-secrets-for-a-repository) to use OIDC or Keys.  Set the role or Keys, but not both:

If you are using the [OIDC method](https://github.com/aws-actions/configure-aws-credentials#OIDC)
- Create a Secret named **AWS_ROLE_TO_ASSUME** and set it to the full ARN of the role
  - It should look something like "arn:aws:iam::123456789:role/github-oidc-provider-Role-nqvduv7P15BZ"

If you are going to use [Access Key and Secret](https://repost.aws/knowledge-center/create-access-key)
- Create a Secret named **AWS_ACCESS_KEY_ID** and set to the Access Key ID
- Create a Secret named **AWS_SECRET_ACCESS_KEY** and set to the Secret Access Key

The workflow has sensible defaults (see below snippet from the workflow), but you will need to set up variables and secrets:

```yaml
env:
  # Create secrets in the repository and they will be pushed to Parameter store, these are required
  # If you don't set an API key for square, you can still use ChatGPT by itself
  SQUARE_API_KEY: ${{ secrets.SQUARE_API_KEY || 'DISABLED' }}
  OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY || 'NEED_TO_SET_THIS' }}  
    
  
  # Create repository variables to override any/all of the below from the defaults
  #
  CDK_STACK_NAME: ${{ vars.CDK_STACK_NAME || 'chatgpt-square-ivr-cdk' }}
  STACK_NAME: ${{ vars.STACK_NAME || 'chatgpt-square-ivr' }}
  
  # The E164 Number to be used when transferring to main number
  TRANSFER_NUMBER: ${{ vars.TRANSFER_NUMBER || '+18004444444' }}
  
  # Set to PRODUCTION if you have a real Sqaure Buisness or Leave it as SANDBOX if you just have a dev account
  SQUARE_ENVIRONMENT: ${{ vars.SQUARE_ENVIRONMENT || 'SANDBOX' }}
  # You can have many locations in Square, need to set to the location you want to query inventory or employees against (required for functions to work)
  SQUARE_LOCATION_ID: ${{ vars.SQUARE_LOCATION_ID || 'DISABLED' }}
  
  # https://platform.openai.com/docs/models/overview (requres model with function calling)
  OPENAI_MODEL: ${{ vars.OPENAI_MODEL || 'gpt-3.5-turbo-1106' }}
  
  # Define in repo variable if you want want to route main number calls via SIP via the Voice Connector
  PBX_HOSTNAME:  ${{ vars.PBX_HOSTNAME || '' }}
  
  # Polly voices to use for English and Spanish https://docs.aws.amazon.com/polly/latest/dg/ntts-voices-main.html
  VOICE_ID_EN: ${{ vars.VOICE_ID_EN  || 'Joanna' }}
  VOICE_ID_ES: ${{ vars.VOICE_ID_ES  || 'Lupe' }}
```

Example [Secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions#creating-secrets-for-a-repository) :

OIDC Method:

![Repository Secrets OIDC](assets/secrets-oidc.png)

Access Keys:

![Repository Secrets Keys](assets/secrets-keys.png)


Example [Variables](https://docs.github.com/en/actions/learn-github-actions/variables#creating-configuration-variables-for-a-repository) :

![Repository Variables](assets/variables.png)

The general steps are:
* [Fork the repository](https://docs.github.com/en/get-started/quickstart/fork-a-repo)
* [Setup required Secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions#creating-secrets-for-a-repository).
  - Setup either OIDC or Access Keys as described above.
  - Create a Secret named **OPENAI_API_KEY** and set to your API key otherwise ChatGPT will not function.
  - Create a Secret named **SQUARE_API_KEY** if are going to be using Square functionaliy, if don't set this, then square functionality will be disabled.
* Optionaly [set any variables](https://docs.github.com/en/actions/learn-github-actions/variables#creating-configuration-variables-for-a-repository) from the defaults like **OPENAI_MODEL** for example.
  - If you are using square then you must also create a variable named **SQUARE_LOCATION_ID** and set to the Location ID you want the API to query.
  - If you are using a production Square API Key, then also create a variable named **SQUARE_ENVIRONMENT** and set it to **PRODUCTION** as the default is **SANDBOX**.
  - Create variables for anything else you want to tweak like stack names, ChatGPT model, etc.

Then start committing changes and watch the magic happen.  CDK deploys regions in parallel and then a matrix job deploys each region in parallel for faster deployments:

![Example Deploy](assets/deploy.png)

### Using the deploy.sh bash script

To deploy this project via CLI, you need the following tools.

* AWS CLI - [Install AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
* AWS SAM CLI - [Install the SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
* AWS CDK - [Instal CDK](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html)
* Java 17 - [Install Java 17](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html)
* Maven - [Install Maven](https://maven.apache.org/install.html)

If you have [brew](https://brew.sh) installed (highly recommended) then:
```bash
brew install awscli
brew install aws-sam-cli
brew install aws-cdk
brew install corretto17
brew install maven

```

To build and deploy, run the following in your shell (or CloudShell) assuming AWS CLI is setup and verified to work.

```bash
git clone https://github.com/docwho2/java-squareup-chatgpt-ivr.git
cd java-squareup-chatgpt-ivr
./deploy.sh

```

You may find it easier to deploy in a [Cloud Shell](https://aws.amazon.com/cloudshell/).  Simply launch a Cloud Shell, then proceed like above.  The deploy script will install maven/Java if it detects you are in a CloudShell.  Note: Due limited storage when using a CloudShell if you have other artifacts and have otherwise used much of the storage, the deploy will fail with space issues (run "df" and check if you experience a failed deployment).

You will see the progress as the stacks deploy.  If you want to change any of the values the script asked for you can simply run it again or as many times as you need.

## Chime SDK Phone Number

Once you have deployed the project either via CLI or Workflow, everything is all SIP.  At this stage you could integrate a PBX (like Asterisk) and call into the application via the Voice Connector that was providioned, however the easiest way to test is to provision a phone number in the AWS Console, then create a SIP Rule to point the phone number to the SMA's created in each region.

After provisioning a [phone number in Chime](https://docs.aws.amazon.com/chime-sdk/latest/ag/provision-phone.html), you will need to create a [SIP Rule](https://docs.aws.amazon.com/chime-sdk/latest/ag/understand-sip-data-models.html) for the phone number. When you call the phone number, you will always be routed to the SMA in the us-east-1 region. Only if that region or the Lambda associated with the SMA goes down will you fail over to the us-west-2 region.


![Chime Phone Targets](assets/chimephonenumber.png)


## Testing

After deploying you can use the [test script](testGPT.sh) to send commands to ChatGPT just to make sure everything is working as planned.  The script collects your input and uses the aws cli to send requests to both regions.

Asking a question like the below example validates several things:
- The Lex Bot is functioning and calling the [ChatGPT Lambda](ChatGPT/src/main/java/cloud/cleo/squareup/ChatGPTLambda.java)
- ChatGPT is functioning properly
  - Your API key is valid and OpenAI service is alive
  - GPT is performing function calling correctly for defined functions
- Square is functioning properly
  - Your Square API Key and location ID are correct
  - Square API's are responding and service is alive
  - Valid data was returned from Square and properly interpreted by GPT

```bash
./testGPT.sh

Enter text to send to ChatGPT Bot [How are you doing today?]: Tell me about your store hours

[Tell me about your store hours] - us-east-1
Response is: 
[We are currently closed. Our regular hours are:
- Friday: 10:00 AM - 5:00 PM
- Saturday: 10:00 AM - 5:00 PM
- Sunday: 11:00 AM - 3:00 PM]


[Tell me about your store hours] - us-west-2
Response is: 
[We are currently closed. Our regular hours are:
- Friday: 10:00 AM - 5:00 PM
- Saturday: 10:00 AM - 5:00 PM
- Sunday: 11:00 AM - 3:00 PM]

```

Testing is also done at deploy time and a couple times a day via Work Flows.
- The [Test Workflow](.github/workflows/tests.yml) can be run at any time manually and also runs daily via cron settings.
- The [Test Action](.github/actions/test/action.yml) is meant to be shared and used in various jobs.  After deploy, tests are run for example, but they can also be run manually with the above mentioned WorkFlow.


## Cleanup

To delete the application and all resources created, use the destroy script.  If you provisioned a phone number, you will need to manually delete that as well or **you will continue to incur charges** for that.

You can run the following:

```bash
./destroy.sh

```

## Sample Deploy Output

```
java-squareup-chatgpt-ivr % ./deploy.sh

ChatGPT won't work if you don't provide a valid API Key, however you can still deploy
Enter your OpenAI API Key [XXXXXXXXXX]: 
Enter OpenAI Model [gpt-3.5-turbo-1106]: 

If you don't have a Square API Key, just hit enter for the next 3 prompts (will disable square functions)

Enter your Square API Key [DISABLED]: 
Enter Square Environment (SANDBOX|PRODUCTION) [SANDBOX]: 
Enter Square Location ID  [DISABLED]: 

Enter transfer # when caller wants to speak to a person [+18004444444]: 


CloudShell not detected, assuming you have Java/Maven/SAM/CDK all installed, if not script will error

Will now ensure CDK is bootstrapped in us-east-1 us-west-2
 ⏳  Bootstrapping environment aws://***/us-east-1...
Trusted accounts for deployment: (none)
Trusted accounts for lookup: (none)
Using default execution policy of 'arn:aws:iam::aws:policy/AdministratorAccess'. Pass '--cloudformation-execution-policies' to customize.

 ✨ hotswap deployment skipped - no changes were detected (use --force to override)

 ✅  Environment aws://***/us-east-1 bootstrapped (no changes).

 ⏳  Bootstrapping environment aws://***/us-west-2...
Trusted accounts for deployment: (none)
Trusted accounts for lookup: (none)
Using default execution policy of 'arn:aws:iam::aws:policy/AdministratorAccess'. Pass '--cloudformation-execution-policies' to customize.

 ✨ hotswap deployment skipped - no changes were detected (use --force to override)

 ✅  Environment aws://***/us-west-2 bootstrapped (no changes).


Deploying CDK stack to us-east-1 us-west-2


✨  Synthesis time: 2.86s

chatgpt-square-ivr-cdk:  start: Building 878abf4f8a52ee049c6a885f81aff403cae67b16991c29d582799c0ac1a22669:***-us-east-1
chatgpt-square-ivr-cdk:  success: Built 878abf4f8a52ee049c6a885f81aff403cae67b16991c29d582799c0ac1a22669:***-us-east-1
chatgpt-square-ivr-cdk:  start: Publishing 878abf4f8a52ee049c6a885f81aff403cae67b16991c29d582799c0ac1a22669:***-us-east-1
chatgpt-square-ivr-cdk:  start: Building c0460130f5069dbb2c5e5c6f98a0100dd798e1c7961ec42393e643ceca3490be:***-us-west-2
chatgpt-square-ivr-cdk:  success: Built c0460130f5069dbb2c5e5c6f98a0100dd798e1c7961ec42393e643ceca3490be:***-us-west-2
chatgpt-square-ivr-cdk:  start: Publishing c0460130f5069dbb2c5e5c6f98a0100dd798e1c7961ec42393e643ceca3490be:***-us-west-2
chatgpt-square-ivr-cdk:  success: Published 878abf4f8a52ee049c6a885f81aff403cae67b16991c29d582799c0ac1a22669:***-us-east-1
east (chatgpt-square-ivr-cdk)
east (chatgpt-square-ivr-cdk): deploying... [1/2]
chatgpt-square-ivr-cdk: creating CloudFormation changeset...
chatgpt-square-ivr-cdk:  success: Published c0460130f5069dbb2c5e5c6f98a0100dd798e1c7961ec42393e643ceca3490be:***-us-west-2
west (chatgpt-square-ivr-cdk)
west (chatgpt-square-ivr-cdk): deploying... [2/2]
chatgpt-square-ivr-cdk: creating CloudFormation changeset...
chatgpt-square-ivr-cdk |  0/19 | 1:40:01 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | smalambdaRole 
chatgpt-square-ivr-cdk |  0/19 | 1:40:01 PM | CREATE_IN_PROGRESS   | AWS::CDK::Metadata          | west/CDKMetadata/Default (CDKMetadata) 
chatgpt-square-ivr-cdk |  0/19 | 1:40:01 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/VC_ARN_PARAM (VCARNPARAMA1F8171A) 
chatgpt-square-ivr-cdk |  0/19 | 1:40:02 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | west/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) 
chatgpt-square-ivr-cdk |  0/19 | 1:39:49 PM | REVIEW_IN_PROGRESS   | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk User Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:39:56 PM | CREATE_IN_PROGRESS   | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk User Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:40:04 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | east/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) Resource creation Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:40:05 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/VC_ARN_PARAM (VCARNPARAMA1F8171A) Resource creation Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:40:05 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | smalambdaRole Resource creation Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:40:05 PM | CREATE_IN_PROGRESS   | AWS::CDK::Metadata          | east/CDKMetadata/Default (CDKMetadata) Resource creation Initiated
chatgpt-square-ivr-cdk |  1/19 | 1:40:05 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | east/VC_ARN_PARAM (VCARNPARAMA1F8171A) 
chatgpt-square-ivr-cdk |  2/19 | 1:40:05 PM | CREATE_COMPLETE      | AWS::CDK::Metadata          | east/CDKMetadata/Default (CDKMetadata) 
chatgpt-square-ivr-cdk |  2/19 | 1:39:48 PM | REVIEW_IN_PROGRESS   | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk User Initiated
chatgpt-square-ivr-cdk |  2/19 | 1:39:59 PM | CREATE_IN_PROGRESS   | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk User Initiated
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | east/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) 
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/VC_ARN_PARAM (VCARNPARAMA1F8171A) 
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::CDK::Metadata          | east/CDKMetadata/Default (CDKMetadata) 
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | smalambdaRole 
chatgpt-square-ivr-cdk |  0/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/VC_ARN_PARAM (VCARNPARAMA1F8171A) Resource creation Initiated
chatgpt-square-ivr-cdk |  0/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::CDK::Metadata          | west/CDKMetadata/Default (CDKMetadata) Resource creation Initiated
chatgpt-square-ivr-cdk |  1/19 | 1:40:03 PM | CREATE_COMPLETE      | AWS::CDK::Metadata          | west/CDKMetadata/Default (CDKMetadata) 
chatgpt-square-ivr-cdk |  1/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | west/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) Resource creation Initiated
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | west/VC_ARN_PARAM (VCARNPARAMA1F8171A) 
chatgpt-square-ivr-cdk |  2/19 | 1:40:03 PM | CREATE_IN_PROGRESS   | AWS::IAM::Role              | smalambdaRole Resource creation Initiated
chatgpt-square-ivr-cdk |  3/19 | 1:40:21 PM | CREATE_COMPLETE      | AWS::IAM::Role              | east/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_COMPLETE      | AWS::IAM::Role              | smalambdaRole 
chatgpt-square-ivr-cdk |  3/19 | 1:40:19 PM | CREATE_COMPLETE      | AWS::IAM::Role              | west/AWS679f53fac002430cb0da5b7982bd2287/ServiceRole (AWS679f53fac002430cb0da5b7982bd2287ServiceRoleC1EA0FF2) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:19 PM | CREATE_COMPLETE      | AWS::IAM::Role              | smalambdaRole 
chatgpt-square-ivr-cdk |  4/19 | 1:40:19 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:20 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:20 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/SR-CR2/CustomResourcePolicy (SRCR2CustomResourcePolicy6283867E) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:20 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:20 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | west/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:20 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | west/sma-lambda (smalambda) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/SR-CR2/CustomResourcePolicy (SRCR2CustomResourcePolicy6283867E) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | west/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | west/sma-lambda (smalambda) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | west/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/SR-CR1/CustomResourcePolicy (SRCR1CustomResourcePolicy9714D179) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:21 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | east/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | east/sma-lambda (smalambda) 
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:22 PM | CREATE_IN_PROGRESS   | AWS::IAM::Policy            | east/SR-CR1/CustomResourcePolicy (SRCR1CustomResourcePolicy9714D179) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:23 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | east/sma-lambda (smalambda) Resource creation Initiated
chatgpt-square-ivr-cdk |  4/19 | 1:40:23 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Function       | east/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) Resource creation Initiated
chatgpt-square-ivr-cdk |  5/19 | 1:40:27 PM | CREATE_COMPLETE      | AWS::Lambda::Function       | west/sma-lambda (smalambda) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:28 PM | CREATE_COMPLETE      | AWS::Lambda::Function       | west/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:28 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/LAMBDAARN (LAMBDAARN5D66CCB0) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:29 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/LAMBDAARN (LAMBDAARN5D66CCB0) Resource creation Initiated
chatgpt-square-ivr-cdk |  5/19 | 1:40:29 PM | CREATE_COMPLETE      | AWS::Lambda::Function       | east/sma-lambda (smalambda) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:29 PM | CREATE_COMPLETE      | AWS::Lambda::Function       | east/AWS679f53fac002430cb0da5b7982bd2287 (AWS679f53fac002430cb0da5b7982bd22872D164C4C) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:29 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/LAMBDAARN (LAMBDAARN5D66CCB0) 
chatgpt-square-ivr-cdk |  6/19 | 1:40:30 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/LAMBDAARN (LAMBDAARN5D66CCB0) Resource creation Initiated
chatgpt-square-ivr-cdk |  7/19 | 1:40:31 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | east/LAMBDAARN (LAMBDAARN5D66CCB0) 
chatgpt-square-ivr-cdk |  7/19 | 1:40:29 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | west/LAMBDAARN (LAMBDAARN5D66CCB0) 
chatgpt-square-ivr-cdk |  8/19 | 1:40:36 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | west/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) 
chatgpt-square-ivr-cdk |  9/19 | 1:40:36 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | west/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) 
chatgpt-square-ivr-cdk | 10/19 | 1:40:37 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | west/SR-CR2/CustomResourcePolicy (SRCR2CustomResourcePolicy6283867E) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:37 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | west/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:37 PM | CREATE_IN_PROGRESS   | Custom::SipMediaApplication | west/SMA-CR/Resource/Default (SMACR6E385B4A) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:37 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnector      | west/VC-CR/Resource/Default (VCCRE7EE978A) 
chatgpt-square-ivr-cdk |  8/19 | 1:40:38 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | east/VC-CR-TERM/CustomResourcePolicy (VCCRTERMCustomResourcePolicy3D87E733) 
chatgpt-square-ivr-cdk |  9/19 | 1:40:38 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | east/SMA-CR/CustomResourcePolicy (SMACRCustomResourcePolicy277D2013) 
chatgpt-square-ivr-cdk | 10/19 | 1:40:38 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | east/SR-CR1/CustomResourcePolicy (SRCR1CustomResourcePolicy9714D179) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:38 PM | CREATE_COMPLETE      | AWS::IAM::Policy            | east/VC-CR/CustomResourcePolicy (VCCRCustomResourcePolicy9739604D) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:39 PM | CREATE_IN_PROGRESS   | Custom::SipMediaApplication | east/SMA-CR/Resource/Default (SMACR6E385B4A) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:39 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnector      | east/VC-CR/Resource/Default (VCCRE7EE978A) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:49 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnector      | west/VC-CR/Resource/Default (VCCRE7EE978A) Resource creation Initiated
chatgpt-square-ivr-cdk | 12/19 | 1:40:49 PM | CREATE_COMPLETE      | Custom::VoiceConnector      | west/VC-CR/Resource/Default (VCCRE7EE978A) 
chatgpt-square-ivr-cdk | 12/19 | 1:40:49 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) 
chatgpt-square-ivr-cdk | 12/19 | 1:40:49 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnectorTerm  | west/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) 
chatgpt-square-ivr-cdk | 12/19 | 1:40:50 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) Resource creation Initiated
chatgpt-square-ivr-cdk | 12/19 | 1:40:50 PM | CREATE_IN_PROGRESS   | Custom::SipMediaApplication | west/SMA-CR/Resource/Default (SMACR6E385B4A) Resource creation Initiated
chatgpt-square-ivr-cdk | 13/19 | 1:40:51 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | west/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:51 PM | CREATE_COMPLETE      | Custom::SipMediaApplication | west/SMA-CR/Resource/Default (SMACR6E385B4A) 
chatgpt-square-ivr-cdk | 11/19 | 1:40:51 PM | CREATE_IN_PROGRESS   | Custom::SipMediaApplication | east/SMA-CR/Resource/Default (SMACR6E385B4A) Resource creation Initiated
chatgpt-square-ivr-cdk | 11/19 | 1:40:51 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnector      | east/VC-CR/Resource/Default (VCCRE7EE978A) Resource creation Initiated
chatgpt-square-ivr-cdk | 12/19 | 1:40:51 PM | CREATE_COMPLETE      | Custom::SipMediaApplication | east/SMA-CR/Resource/Default (SMACR6E385B4A) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:51 PM | CREATE_COMPLETE      | Custom::VoiceConnector      | east/VC-CR/Resource/Default (VCCRE7EE978A) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/SMA_ID_PARAM (SMAIDPARAM0A524744) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Permission     | east/SMA-CR-PERM (SMACRPERM) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnectorTerm  | east/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | Custom::SipRule             | east/SR-CR1/Resource/Default (SRCR17A38CCA2) 
chatgpt-square-ivr-cdk | 13/19 | 1:40:53 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/SMA_ID_PARAM (SMAIDPARAM0A524744) Resource creation Initiated
chatgpt-square-ivr-cdk | 13/19 | 1:40:53 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Permission     | east/SMA-CR-PERM (SMACRPERM) Resource creation Initiated
chatgpt-square-ivr-cdk | 14/19 | 1:40:53 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | east/SMA_ID_PARAM (SMAIDPARAM0A524744) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:53 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | east/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) Resource creation Initiated
chatgpt-square-ivr-cdk | 15/19 | 1:40:53 PM | CREATE_COMPLETE      | AWS::Lambda::Permission     | east/SMA-CR-PERM (SMACRPERM) 
chatgpt-square-ivr-cdk | 16/19 | 1:40:53 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | east/VC_HOSTNAME_PARAM (VCHOSTNAMEPARAM2165CF79) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:51 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/SMA_ID_PARAM (SMAIDPARAM0A524744) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:51 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Permission     | west/SMA-CR-PERM (SMACRPERM) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:51 PM | CREATE_IN_PROGRESS   | Custom::SipRule             | west/SR-CR2/Resource/Default (SRCR2E1269187) 
chatgpt-square-ivr-cdk | 14/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnectorTerm  | west/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) Resource creation Initiated
chatgpt-square-ivr-cdk | 15/19 | 1:40:52 PM | CREATE_COMPLETE      | Custom::VoiceConnectorTerm  | west/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) 
chatgpt-square-ivr-cdk | 15/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | AWS::SSM::Parameter         | west/SMA_ID_PARAM (SMAIDPARAM0A524744) Resource creation Initiated
chatgpt-square-ivr-cdk | 15/19 | 1:40:52 PM | CREATE_IN_PROGRESS   | AWS::Lambda::Permission     | west/SMA-CR-PERM (SMACRPERM) Resource creation Initiated
chatgpt-square-ivr-cdk | 16/19 | 1:40:52 PM | CREATE_COMPLETE      | AWS::SSM::Parameter         | west/SMA_ID_PARAM (SMAIDPARAM0A524744) 
chatgpt-square-ivr-cdk | 17/19 | 1:40:53 PM | CREATE_COMPLETE      | AWS::Lambda::Permission     | west/SMA-CR-PERM (SMACRPERM) 
chatgpt-square-ivr-cdk | 17/19 | 1:40:53 PM | CREATE_IN_PROGRESS   | Custom::SipRule             | west/SR-CR2/Resource/Default (SRCR2E1269187) Resource creation Initiated
chatgpt-square-ivr-cdk | 18/19 | 1:40:54 PM | CREATE_COMPLETE      | Custom::SipRule             | west/SR-CR2/Resource/Default (SRCR2E1269187) 
chatgpt-square-ivr-cdk | 19/19 | 1:40:55 PM | CREATE_COMPLETE      | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk 

 ✅  west (chatgpt-square-ivr-cdk)

✨  Deployment time: 67.89s

Outputs:
west.SMAID = 361b1f39-cf7b-4f35-b04f-0a39e2876b38
west.VCHOSTNAME = b7myixompli7rcb5hdwwrm.voiceconnector.chime.aws
Stack ARN:
arn:aws:cloudformation:us-west-2:***:stack/chatgpt-square-ivr-cdk/1380a090-80ca-11ee-a788-0ac549880e8d

✨  Total time: 70.75s

chatgpt-square-ivr-cdk | 16/19 | 1:40:54 PM | CREATE_IN_PROGRESS   | Custom::VoiceConnectorTerm  | east/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) Resource creation Initiated
chatgpt-square-ivr-cdk | 17/19 | 1:40:54 PM | CREATE_COMPLETE      | Custom::VoiceConnectorTerm  | east/VC-CR-TERM/Resource/Default (VCCRTERM11C63EB8) 
chatgpt-square-ivr-cdk | 17/19 | 1:40:54 PM | CREATE_IN_PROGRESS   | Custom::SipRule             | east/SR-CR1/Resource/Default (SRCR17A38CCA2) Resource creation Initiated
chatgpt-square-ivr-cdk | 18/19 | 1:40:54 PM | CREATE_COMPLETE      | Custom::SipRule             | east/SR-CR1/Resource/Default (SRCR17A38CCA2) 
chatgpt-square-ivr-cdk | 19/19 | 1:40:56 PM | CREATE_COMPLETE      | AWS::CloudFormation::Stack  | chatgpt-square-ivr-cdk 

 ✅  east (chatgpt-square-ivr-cdk)

✨  Deployment time: 71.36s

Outputs:
east.SMAID = bc08cbfe-e007-46f6-bd08-0e71707d9da8
east.VCHOSTNAME = elb3optyef4vu5djzcti20.voiceconnector.chime.aws
Stack ARN:
arn:aws:cloudformation:us-east-1:***:stack/chatgpt-square-ivr-cdk/12c2f5e0-80ca-11ee-b7c2-0a9923416cf1

✨  Total time: 74.21s



Building Libraries
[INFO] Scanning for projects...
[INFO] ------------------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] AWS Lambda Java Runtime Serialization 2.0.0 ........ SUCCESS [  0.400 s]
[INFO] AWS Lambda Java Events Library 4.0.0 ............... SUCCESS [  0.147 s]
[INFO] Chime SMA Parent POM 1.0 ........................... SUCCESS [  0.001 s]
[INFO] Chime SDK SMA Event Library 1.0 .................... SUCCESS [  0.018 s]
[INFO] Chime SDK SMA Flow Library 1.0 ..................... SUCCESS [  0.028 s]
[INFO] Chime Polly Prompt Generator 1.0 ................... SUCCESS [  1.524 s]
[INFO] Chime Lex ChatGPT Lambda 1.0 ....................... SUCCESS [  1.834 s]
[INFO] Chime SDK SMA Examples 1.0 ......................... SUCCESS [  1.071 s]
[INFO] Chime Voice CDK Provision 1.0 ...................... SUCCESS [  0.345 s]
[INFO] Square Chime SMA Parent POM 1.0 .................... SUCCESS [  0.002 s]
[INFO] Square Chime Lex ChatGPT Lambda 1.0 ................ SUCCESS [  2.429 s]
[INFO] Square Chime SMA Lambda 1.0 ........................ SUCCESS [  0.807 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.681 s
[INFO] Finished at: 2023-11-11T13:41:08-06:00
[INFO] ------------------------------------------------------------------------
                                                                                                                     
Building codeuri: /Users/sjensen/dude/java-squareup-chatgpt-ivr/ChatGPT runtime: java17 metadata: {} architecture: x86_64 functions: ChatGPT                                                                     
 Running JavaMavenWorkflow:CopySource                                                                                                                                                                            
 Running JavaMavenWorkflow:MavenBuild                                                                                                                                                                            
 Running JavaMavenWorkflow:MavenCopyDependency                                                                                                                                                                   
 Running JavaMavenWorkflow:MavenCopyArtifacts                                                                                                                                                                    
 Running JavaMavenWorkflow:CleanUp                                                                                                                                                                               
 Running JavaMavenWorkflow:JavaCopyDependencies                                                                                                                                                                  

Build Succeeded

Built Artifacts  : .aws-sam/build
Built Template   : .aws-sam/build/template.yaml

Commands you can use next
=========================
[*] Validate SAM template: sam validate
[*] Invoke Function: sam local invoke
[*] Test Function in the Cloud: sam sync --stack-name {{stack-name}} --watch
[*] Deploy: sam deploy --guided

		Managed S3 bucket: aws-sam-cli-managed-default-samclisourcebucket-13mtysy565mpu
		A different default S3 bucket can be set in samconfig.toml
		Or by specifying --s3-bucket explicitly.
	Uploading to edbb4caca275e3789a7e7b323715310e  18709716 / 18709716  (100.00%)
File with same data already exists at edbb4caca275e3789a7e7b323715310e, skipping upload                                                                                                                          
	Uploading to 8e59dd1bc032ccd9e5d603662d4af6aa  12156120 / 12156120  (100.00%)
	Uploading to 03a1e0f143a451b90f9f90e595685355  33735650 / 33735650  (100.00%)

	Deploying with following values
	===============================
	Stack name                   : chatgpt-square-ivr
	Region                       : us-east-1
	Confirm changeset            : False
	Disable rollback             : False
	Deployment s3 bucket         : aws-sam-cli-managed-default-samclisourcebucket-13mtysy565mpu
	Capabilities                 : ["CAPABILITY_IAM"]
	Parameter overrides          : {"OPENAIAPIKEY": "/chatgpt-square-ivr/OPENAI_API_KEY", "SMAID": "/chatgpt-square-ivr-cdk/SMA_ID", "VOICECONNECTORARN": "/chatgpt-square-ivr-cdk/VC_ARN", "SQUAREENVIRONMENT": "SANDBOX", "SQUARELOCATIONID": "DISABLED", "TRANSFERNUMBER": "+18004444444", "OPENAIMODEL": "gpt-3.5-turbo-1106", "VOICEIDEN": "Joanna", "VOICEIDES": "Lupe"}
	Signing Profiles             : {}

Initiating deployment
=====================

	Uploading to 57f7012144864dea8bceb3c43ccd2e92.template  16416 / 16416  (100.00%)


Waiting for changeset to be created..

CloudFormation stack changeset
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Operation                                           LogicalResourceId                                   ResourceType                                        Replacement                                       
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
+ Add                                               BotAliasGPT                                         AWS::Lex::BotAlias                                  N/A                                               
+ Add                                               BotRuntimeRole                                      AWS::IAM::Role                                      N/A                                               
+ Add                                               BotVersionGPT                                       AWS::Lex::BotVersion                                N/A                                               
+ Add                                               ChatGPTAliasSNAPSTART                               AWS::Lambda::Alias                                  N/A                                               
+ Add                                               ChatGPTRole                                         AWS::IAM::Role                                      N/A                                               
+ Add                                               ChatGPTVersiona09db0da17                            AWS::Lambda::Version                                N/A                                               
+ Add                                               ChatGPT                                             AWS::Lambda::Function                               N/A                                               
+ Add                                               ChimeCallLexGPT                                     AWS::Lex::ResourcePolicy                            N/A                                               
+ Add                                               ChimeSMAAliasSNAPSTART                              AWS::Lambda::Alias                                  N/A                                               
+ Add                                               ChimeSMAPerm                                        AWS::Lambda::Permission                             N/A                                               
+ Add                                               ChimeSMARole                                        AWS::IAM::Role                                      N/A                                               
+ Add                                               ChimeSMASnapPerm                                    AWS::Lambda::Permission                             N/A                                               
+ Add                                               ChimeSMAVersiondb12bc9449                           AWS::Lambda::Version                                N/A                                               
+ Add                                               ChimeSMA                                            AWS::Lambda::Function                               N/A                                               
+ Add                                               ClosedEN                                            Custom::PromptCreator                               N/A                                               
+ Add                                               GoodbyePromptEN                                     Custom::PromptCreator                               N/A                                               
+ Add                                               GoodbyePromptES                                     Custom::PromptCreator                               N/A                                               
+ Add                                               LexBotGPT                                           AWS::Lex::Bot                                       N/A                                               
+ Add                                               LexToChatGPTPerm                                    AWS::Lambda::Permission                             N/A                                               
+ Add                                               LexToChatGPTSnapPerm                                AWS::Lambda::Permission                             N/A                                               
+ Add                                               MainPromptEast                                      Custom::PromptCreator                               N/A                                               
+ Add                                               OpenEN                                              Custom::PromptCreator                               N/A                                               
+ Add                                               PromptBucketPolicy                                  AWS::S3::BucketPolicy                               N/A                                               
+ Add                                               PromptBucket                                        AWS::S3::Bucket                                     N/A                                               
+ Add                                               PromptCopierRole                                    AWS::IAM::Role                                      N/A                                               
+ Add                                               PromptCopier                                        AWS::Lambda::Function                               N/A                                               
+ Add                                               PromptCreatorRole                                   AWS::IAM::Role                                      N/A                                               
+ Add                                               PromptCreator                                       AWS::Lambda::Function                               N/A                                               
+ Add                                               SessionTable                                        AWS::DynamoDB::Table                                N/A                                               
+ Add                                               StaticPrompts                                       Custom::PromptCopier                                N/A                                               
+ Add                                               TransferEN                                          Custom::PromptCreator                               N/A                                               
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


Changeset created successfully. arn:aws:cloudformation:us-east-1:***:changeSet/samcli-deploy1699731736/0c063172-8389-4763-b45a-f8ee80e49c72


2023-11-11 13:42:33 - Waiting for stack create/update to complete

CloudFormation events from stack operations (refresh every 5.0 seconds)
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
ResourceStatus                                      ResourceType                                        LogicalResourceId                                   ResourceStatusReason                              
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
CREATE_IN_PROGRESS                                  AWS::CloudFormation::Stack                          chatgpt-square-ivr                                  User Initiated                                    
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChimeSMARole                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::Bucket                                     PromptBucket                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::DynamoDB::Table                                SessionTable                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      BotRuntimeRole                                      -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::Bucket                                     PromptBucket                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      BotRuntimeRole                                      Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::DynamoDB::Table                                SessionTable                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChimeSMARole                                        Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::DynamoDB::Table                                SessionTable                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChatGPTRole                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChatGPTRole                                         Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::IAM::Role                                      ChimeSMARole                                        -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      BotRuntimeRole                                      -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::Bot                                       LexBotGPT                                           -                                                 
CREATE_COMPLETE                                     AWS::S3::Bucket                                     PromptBucket                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCreatorRole                                   -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::BucketPolicy                               PromptBucketPolicy                                  -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCopierRole                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::Bot                                       LexBotGPT                                           Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCreatorRole                                   Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCopierRole                                    Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::S3::BucketPolicy                               PromptBucketPolicy                                  Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::S3::BucketPolicy                               PromptBucketPolicy                                  -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      ChatGPTRole                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChatGPT                                             -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChatGPT                                             Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::IAM::Role                                      PromptCreatorRole                                   -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      PromptCopierRole                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCreator                                       -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCopier                                        -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               ChatGPT                                             -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChatGPTVersiona09db0da17                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCreator                                       Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCopier                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTPerm                                    Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             LexToChatGPTPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChatGPTVersiona09db0da17                            Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Function                               PromptCopier                                        -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               PromptCreator                                       -                                                 
CREATE_COMPLETE                                     AWS::Lex::Bot                                       LexBotGPT                                           -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCopier                                StaticPrompts                                       -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptES                                     -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               TransferEN                                          -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               OpenEN                                              -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               MainPromptEast                                      -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               ClosedEN                                            -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptEN                                     -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotVersion                                BotVersionGPT                                       -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotVersion                                BotVersionGPT                                       Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptES                                     Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCopier                                StaticPrompts                                       Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptEN                                     Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               MainPromptEast                                      Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               OpenEN                                              Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCreator                               GoodbyePromptES                                     -                                                 
CREATE_COMPLETE                                     Custom::PromptCopier                                StaticPrompts                                       -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               TransferEN                                          Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCreator                               GoodbyePromptEN                                     -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               MainPromptEast                                      -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               OpenEN                                              -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               TransferEN                                          -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               ClosedEN                                            Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCreator                               ClosedEN                                            -                                                 
CREATE_COMPLETE                                     AWS::Lex::BotVersion                                BotVersionGPT                                       -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Version                                ChatGPTVersiona09db0da17                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotAlias                                  BotAliasGPT                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotAlias                                  BotAliasGPT                                         Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lex::BotAlias                                  BotAliasGPT                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChimeSMA                                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChimeSMA                                            Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               ChimeSMA                                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChimeSMAVersiondb12bc9449                           -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMAPerm                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMAPerm                                        Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             ChimeSMAPerm                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChimeSMAVersiondb12bc9449                           Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Version                                ChimeSMAVersiondb12bc9449                           -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMASnapPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMASnapPerm                                    Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             ChimeSMASnapPerm                                    -                                                 
CREATE_COMPLETE                                     AWS::CloudFormation::Stack                          chatgpt-square-ivr                                  -                                                 
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


Successfully created/updated stack - chatgpt-square-ivr in us-east-1


		Managed S3 bucket: aws-sam-cli-managed-default-samclisourcebucket-m21j5av2movi
		A different default S3 bucket can be set in samconfig.toml
		Or by specifying --s3-bucket explicitly.
	Uploading to edbb4caca275e3789a7e7b323715310e  18709716 / 18709716  (100.00%)
File with same data already exists at edbb4caca275e3789a7e7b323715310e, skipping upload                                                                                                                          
	Uploading to 8e59dd1bc032ccd9e5d603662d4af6aa  12156120 / 12156120  (100.00%)
	Uploading to 03a1e0f143a451b90f9f90e595685355  33735650 / 33735650  (100.00%)

	Deploying with following values
	===============================
	Stack name                   : chatgpt-square-ivr
	Region                       : us-west-2
	Confirm changeset            : False
	Disable rollback             : False
	Deployment s3 bucket         : aws-sam-cli-managed-default-samclisourcebucket-m21j5av2movi
	Capabilities                 : ["CAPABILITY_IAM"]
	Parameter overrides          : {"OPENAIAPIKEY": "/chatgpt-square-ivr/OPENAI_API_KEY", "SMAID": "/chatgpt-square-ivr-cdk/SMA_ID", "VOICECONNECTORARN": "/chatgpt-square-ivr-cdk/VC_ARN", "SQUAREENVIRONMENT": "SANDBOX", "SQUARELOCATIONID": "DISABLED", "TRANSFERNUMBER": "+18004444444", "OPENAIMODEL": "gpt-3.5-turbo-1106", "VOICEIDEN": "Joanna", "VOICEIDES": "Lupe"}
	Signing Profiles             : {}

Initiating deployment
=====================

	Uploading to c00f16ae3c0dda98779c01bf739809bb.template  16412 / 16412  (100.00%)


Waiting for changeset to be created..

CloudFormation stack changeset
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Operation                                           LogicalResourceId                                   ResourceType                                        Replacement                                       
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
+ Add                                               BotAliasGPT                                         AWS::Lex::BotAlias                                  N/A                                               
+ Add                                               BotRuntimeRole                                      AWS::IAM::Role                                      N/A                                               
+ Add                                               BotVersionGPT                                       AWS::Lex::BotVersion                                N/A                                               
+ Add                                               ChatGPTAliasSNAPSTART                               AWS::Lambda::Alias                                  N/A                                               
+ Add                                               ChatGPTRole                                         AWS::IAM::Role                                      N/A                                               
+ Add                                               ChatGPTVersion0f137f08d8                            AWS::Lambda::Version                                N/A                                               
+ Add                                               ChatGPT                                             AWS::Lambda::Function                               N/A                                               
+ Add                                               ChimeCallLexGPT                                     AWS::Lex::ResourcePolicy                            N/A                                               
+ Add                                               ChimeSMAAliasSNAPSTART                              AWS::Lambda::Alias                                  N/A                                               
+ Add                                               ChimeSMAPerm                                        AWS::Lambda::Permission                             N/A                                               
+ Add                                               ChimeSMARole                                        AWS::IAM::Role                                      N/A                                               
+ Add                                               ChimeSMASnapPerm                                    AWS::Lambda::Permission                             N/A                                               
+ Add                                               ChimeSMAVersion3bae246093                           AWS::Lambda::Version                                N/A                                               
+ Add                                               ChimeSMA                                            AWS::Lambda::Function                               N/A                                               
+ Add                                               ClosedEN                                            Custom::PromptCreator                               N/A                                               
+ Add                                               GoodbyePromptEN                                     Custom::PromptCreator                               N/A                                               
+ Add                                               GoodbyePromptES                                     Custom::PromptCreator                               N/A                                               
+ Add                                               LexBotGPT                                           AWS::Lex::Bot                                       N/A                                               
+ Add                                               LexToChatGPTPerm                                    AWS::Lambda::Permission                             N/A                                               
+ Add                                               LexToChatGPTSnapPerm                                AWS::Lambda::Permission                             N/A                                               
+ Add                                               MainPromptEast                                      Custom::PromptCreator                               N/A                                               
+ Add                                               OpenEN                                              Custom::PromptCreator                               N/A                                               
+ Add                                               PromptBucketPolicy                                  AWS::S3::BucketPolicy                               N/A                                               
+ Add                                               PromptBucket                                        AWS::S3::Bucket                                     N/A                                               
+ Add                                               PromptCopierRole                                    AWS::IAM::Role                                      N/A                                               
+ Add                                               PromptCopier                                        AWS::Lambda::Function                               N/A                                               
+ Add                                               PromptCreatorRole                                   AWS::IAM::Role                                      N/A                                               
+ Add                                               PromptCreator                                       AWS::Lambda::Function                               N/A                                               
+ Add                                               SessionTable                                        AWS::DynamoDB::Table                                N/A                                               
+ Add                                               StaticPrompts                                       Custom::PromptCopier                                N/A                                               
+ Add                                               TransferEN                                          Custom::PromptCreator                               N/A                                               
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


Changeset created successfully. arn:aws:cloudformation:us-west-2:***:changeSet/samcli-deploy1699732145/bec8de1f-78d5-4e54-8853-aba4164c6b4a


2023-11-11 13:49:18 - Waiting for stack create/update to complete

CloudFormation events from stack operations (refresh every 5.0 seconds)
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
ResourceStatus                                      ResourceType                                        LogicalResourceId                                   ResourceStatusReason                              
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
CREATE_IN_PROGRESS                                  AWS::CloudFormation::Stack                          chatgpt-square-ivr                                  User Initiated                                    
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChimeSMARole                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::DynamoDB::Table                                SessionTable                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      BotRuntimeRole                                      -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::Bucket                                     PromptBucket                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::Bucket                                     PromptBucket                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::DynamoDB::Table                                SessionTable                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      BotRuntimeRole                                      Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChimeSMARole                                        Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::DynamoDB::Table                                SessionTable                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChatGPTRole                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      ChatGPTRole                                         Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::IAM::Role                                      ChimeSMARole                                        -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      BotRuntimeRole                                      -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::Bot                                       LexBotGPT                                           -                                                 
CREATE_COMPLETE                                     AWS::S3::Bucket                                     PromptBucket                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCopierRole                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCreatorRole                                   -                                                 
CREATE_IN_PROGRESS                                  AWS::S3::BucketPolicy                               PromptBucketPolicy                                  -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::Bot                                       LexBotGPT                                           Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCopierRole                                    Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::S3::BucketPolicy                               PromptBucketPolicy                                  Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::IAM::Role                                      PromptCreatorRole                                   Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::S3::BucketPolicy                               PromptBucketPolicy                                  -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      ChatGPTRole                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChatGPT                                             -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChatGPT                                             Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::IAM::Role                                      PromptCopierRole                                    -                                                 
CREATE_COMPLETE                                     AWS::IAM::Role                                      PromptCreatorRole                                   -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               ChatGPT                                             -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCopier                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCreator                                       -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChatGPTVersion0f137f08d8                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCopier                                        Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               PromptCreator                                       Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTPerm                                    Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             LexToChatGPTPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChatGPTVersion0f137f08d8                            Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lex::Bot                                       LexBotGPT                                           -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               PromptCopier                                        -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               PromptCreator                                       -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCopier                                StaticPrompts                                       -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptES                                     -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               MainPromptEast                                      -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptEN                                     -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               ClosedEN                                            -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               OpenEN                                              -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               TransferEN                                          -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotVersion                                BotVersionGPT                                       -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotVersion                                BotVersionGPT                                       Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCopier                                StaticPrompts                                       Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCopier                                StaticPrompts                                       -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptES                                     Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               TransferEN                                          Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               GoodbyePromptEN                                     Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               OpenEN                                              Resource creation Initiated                       
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               ClosedEN                                            Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCreator                               GoodbyePromptES                                     -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               TransferEN                                          -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               GoodbyePromptEN                                     -                                                 
CREATE_IN_PROGRESS                                  Custom::PromptCreator                               MainPromptEast                                      Resource creation Initiated                       
CREATE_COMPLETE                                     Custom::PromptCreator                               OpenEN                                              -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               ClosedEN                                            -                                                 
CREATE_COMPLETE                                     Custom::PromptCreator                               MainPromptEast                                      -                                                 
CREATE_COMPLETE                                     AWS::Lex::BotVersion                                BotVersionGPT                                       -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Version                                ChatGPTVersion0f137f08d8                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Alias                                  ChatGPTAliasSNAPSTART                               -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::BotAlias                                  BotAliasGPT                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lex::BotAlias                                  BotAliasGPT                                         Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             LexToChatGPTSnapPerm                                -                                                 
CREATE_COMPLETE                                     AWS::Lex::BotAlias                                  BotAliasGPT                                         -                                                 
CREATE_IN_PROGRESS                                  AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChimeSMA                                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Function                               ChimeSMA                                            Resource creation Initiated                       
CREATE_IN_PROGRESS                                  AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lex::ResourcePolicy                            ChimeCallLexGPT                                     -                                                 
CREATE_COMPLETE                                     AWS::Lambda::Function                               ChimeSMA                                            -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMAPerm                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChimeSMAVersion3bae246093                           -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMAPerm                                        Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             ChimeSMAPerm                                        -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Version                                ChimeSMAVersion3bae246093                           Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Version                                ChimeSMAVersion3bae246093                           -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Alias                                  ChimeSMAAliasSNAPSTART                              -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMASnapPerm                                    -                                                 
CREATE_IN_PROGRESS                                  AWS::Lambda::Permission                             ChimeSMASnapPerm                                    Resource creation Initiated                       
CREATE_COMPLETE                                     AWS::Lambda::Permission                             ChimeSMASnapPerm                                    -                                                 
CREATE_COMPLETE                                     AWS::CloudFormation::Stack                          chatgpt-square-ivr                                  -                                                 
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


Successfully created/updated stack - chatgpt-square-ivr in us-west-2


Congrats!  You have deployed the ChatGPT IVR for Square Retail


You can now go to the AWS Admin Console and provision a phone number and create a SIP Rule pointing to the SIP Media App's
  https://docs.aws.amazon.com/chime-sdk/latest/ag/provision-phone.html
  https://docs.aws.amazon.com/chime-sdk/latest/ag/understand-sip-data-models.html

Point Your Phone Number SIP Rule to:
  SMA ID bc08cbfe-e007-46f6-bd08-0e71707d9da8 in region us-east-1
  SMA ID 361b1f39-cf7b-4f35-b04f-0a39e2876b38 in region us-west-2
```
