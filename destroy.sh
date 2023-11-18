#!/bin/bash

# Stack names and regions
source config.sh


ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# delete things in each region
for region in "${regions[@]}"; do

# Delete the stack
echo
echo "Calling SAM delete on stack in region ${region}"
sam delete --region ${region} --stack-name ${STACK_NAME} --no-prompts 

# Delete the API Keys that were pushed
echo
echo "Deleting API keys from SSM Parameter Store in region ${region}"
aws ssm --region ${region} delete-parameters --names "/${STACK_NAME}/OPENAI_API_KEY" "/${STACK_NAME}/SQUARE_API_KEY" > /dev/null

# Delete the Custom Resource Log groups because if CF deletes it, the final delete of Lambda re-creates it
echo
echo "Deleting Log Group /aws/lambda/${STACK_NAME}-PromptCreator  in region ${region}"
aws logs delete-log-group --region ${region} --log-group-name "/aws/lambda/${STACK_NAME}-PromptCreator" > /dev/null
echo
echo "Deleting Log Group /aws/lambda/${STACK_NAME}-PromptCopier  in region ${region}"
aws logs delete-log-group --region ${region} --log-group-name "/aws/lambda/${STACK_NAME}-PromptCopier" > /dev/null

done

pushd "ChimeCDKProvision"
echo
echo "Calling Destroy on CDK Stack to remove all Chime Voice SDK resources"
cdk destroy -c accountId=${ACCOUNT_ID} -c stackName=${CDK_STACK_NAME} -c regionEast=${regions[0]} -c regionWest=${regions[1]} --all --force

wait






