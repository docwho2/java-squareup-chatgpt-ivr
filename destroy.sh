#!/bin/bash

# Stack names and regions
source config.sh


# Exit immediately if a command exits with a non-zero status.
set -e

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)


for region in "${regions[@]}"; do

sam delete --region ${region} --stack-name ${STACK_NAME} --no-prompts &
aws ssm --region ${region} delete-parameters --names "/${STACK_NAME}/OPENAI_API_KEY" "/${STACK_NAME}/SQUARE_API_KEY" > /dev/null

done

pushd "ChimeCDKProvision"
cdk destroy -c accountId=${ACCOUNT_ID} -c stackName=${CDK_STACK_NAME} -c regionEast=${regions[0]} -c regionWest=${regions[1]} --all --force

wait






