#!/bin/bash
CDK_STACK_NAME=cfox-chime-cdk-provision
STACK_NAME=cfox-squareup-chatgpt-ivr
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo $ACCOUNT_ID
echo /$CDK_STACK_NAME/SMA_ID 
SMA_ID=$(aws ssm get-parameter --name /$CDK_STACK_NAME/SMA_ID --query Parameter.Value --output text)
echo "SMAID is $SMA_ID"
CUR_ENDPOINT=$(aws chime get-sip-media-application --sip-media-application-id $SMA_ID --query 'SipMediaApplication.Endpoints[0].LambdaArn' --output text)
echo $CUR_ENDPOINT
TARGET_ENDPOINT=arn:aws:lambda:us-east-1:$ACCOUNT_ID:function:$STACK_NAME-ChimeSMA
if [[ $TARGET_ENDPOINT != $CUR_ENDPOINT ]]; then
  echo "SMA Lambda Endpoint needs to be updated"
  aws chime update-sip-media-application --sip-media-application-id $SMA_ID --endpoints LambdaArn=$TARGET_ENDPOINT
else
  echo "SMA Lambda Endpoint is already corectly set"
fi
