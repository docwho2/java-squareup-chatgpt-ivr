#!/bin/bash

# Stack names and regions
source config.sh

# Polly voices
VOICE_ID_EN=Joanna
VOICE_ID_ES=Lupe


# Check if AWS CLI is installed
if ! command -v aws >/dev/null 2>&1; then
    echo "AWS CLI is not installed. Please install it to proceed." >&2
    exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
if [ $? -ne 0 ]; then
    echo "Unable to execute AWS CLI command, did you run 'aws configure' and test the CLI?"
    exit 1
fi

# Exit immediately if a command exits with a non-zero status.
set -e



# OpenAI API Key
default_value="XXXXXXXXXX"
echo "ChatGPT won't work if you don't provide a valid API Key, however you can still deploy"
read -p "Enter your OpenAI API Key [${default_value}]: " OPENAI_API_KEY
OPENAI_API_KEY=${OPENAI_API_KEY:-$default_value}

# Model
default_value="gpt-3.5-turbo-1106"
read -p "Enter OpenAI Model [${default_value}]: " OPENAI_MODEL
OPENAI_MODEL=${OPENAI_MODEL:-$default_value}

# Square API Key
echo
echo "If you don't have a Square API Key, just hit enter for the next 3 prompts (will disable square functions)"
echo
default_value=""
read -p "Enter your Square API Key [${default_value}]: " SQUARE_API_KEY
SQUARE_API_KEY=${SQUARE_API_KEY:-$default_value}

default_value="SANDBOX"
read -p "Enter Square Environment (SANDBOX|PRODUCTION) [${default_value}]: " SQUARE_ENVIRONMENT
SQUARE_ENVIRONMENT=${SQUARE_ENVIRONMENT:-$default_value}

default_value=""
read -p "Enter Square Location ID  [${default_value}]: " SQUARE_LOCATION_ID
SQUARE_LOCATION_ID=${SQUARE_LOCATION_ID:-$default_value}

echo
default_value="+18004444444"
read -p "Enter transfer # when caller wants to speak to a person [${default_value}]: " TRANSFER_NUMBER
TRANSFER_NUMBER=${TRANSFER_NUMBER:-$default_value}


echo



if [ "$AWS_EXECUTION_ENV" = "CloudShell" ]; then
    echo "CloudShell Detected, installing maven dependency"
    # gives you maven, corretto 17
    sudo yum -y install maven
    # Ensure we are on latest CDK
    sudo npm install -g aws-cdk
else
    echo
    echo "CloudShell not detected, assuming you have Java/Maven/SAM/CDK all installed, if not script will error"
    echo
fi

# ensure sub modules are brought in
git submodule update --init --recursive

echo "Will now ensure CDK is bootstrapped in ${regions[@]}"
for region in "${regions[@]}"; do
    cdk bootstrap aws://${ACCOUNT_ID}/${region} -c accountId=${ACCOUNT_ID}
done

echo
echo "Deploying CDK stack to ${regions[@]}"
echo
pushd "ChimeCDKProvision"
cdk deploy -c accountId=${ACCOUNT_ID} -c stackName=${CDK_STACK_NAME} -c regionEast=${regions[0]} -c regionWest=${regions[1]} --concurrency=2 --all --require-approval=never --ci=true
popd

echo "Building Libraries"
mvn -B install -DskipTests

# Build App Once
sam build


for region in "${regions[@]}"; do

aws ssm put-parameter \
        --name /${STACK_NAME}/OPENAI_API_KEY \
        --description "OpenAI API Key used for stack ${STACK_NAME}" \
        --type String \
        --value ${OPENAI_API_KEY} \
        --overwrite \
        --region ${region} > /dev/null

aws ssm put-parameter \
        --name /${STACK_NAME}/SQUARE_API_KEY \
        --description "Square API Key used for stack ${STACK_NAME}" \
        --type String \
        --value ${SQUARE_API_KEY} \
        --overwrite \
        --region ${region} > /dev/null

sam deploy --no-fail-on-empty-changeset --no-confirm-changeset \
--region ${region} \
--stack-name ${STACK_NAME} \
--parameter-overrides "\ 
SQUAREAPIKEY=/${STACK_NAME}/SQUARE_API_KEY \
OPENAIAPIKEY=/${STACK_NAME}/OPENAI_API_KEY \
SMAID=/${CDK_STACK_NAME}/SMA_ID \
VOICECONNECTORARN=/${CDK_STACK_NAME}/VC_ARN \
SQUAREENVIRONMENT=${SQUARE_ENVIRONMENT} \
SQUARELOCATIONID=${SQUARE_LOCATION_ID} \
TRANSFERNUMBER=${TRANSFER_NUMBER} \
OPENAIMODEL=${OPENAI_MODEL} \
VOICEIDEN=${VOICE_ID_EN} \
VOICEIDES=${VOICE_ID_ES}"

TARGET_ENDPOINT=arn:aws:lambda:${region}:${ACCOUNT_ID}:function:${STACK_NAME}-ChimeSMA
SMA_ID=$(aws ssm get-parameter --region ${region} --name /${CDK_STACK_NAME}/SMA_ID --query Parameter.Value --output text)
aws chime-sdk-voice update-sip-media-application --region ${region} --sip-media-application-id ${SMA_ID} --endpoints LambdaArn=${TARGET_ENDPOINT} > /dev/null

done

echo
echo "Congrats!  You have deployed the ChatGPT IVR for Square Retail"
echo
echo
echo "You can now go to the AWS Admin Console and provision a phone number and create a SIP Rule pointing it to SIP Media App's"
echo "  https://docs.aws.amazon.com/chime-sdk/latest/ag/provision-phone.html"
echo "  https://docs.aws.amazon.com/chime-sdk/latest/ag/understand-sip-data-models.html"
echo
echo "Point Your SIP Rule to:"
for region in "${regions[@]}"; do
    SMA_ID=$(aws ssm get-parameter --region ${region} --name /${CDK_STACK_NAME}/SMA_ID --query Parameter.Value --output text)
    echo "  SMA ID ${SMA_ID} in region ${region}"
done
