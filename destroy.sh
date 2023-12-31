#!/bin/bash
#
# Delete and clean up all resources


# Stack names and regions
source config.sh

if [ "$AWS_EXECUTION_ENV" = "CloudShell" ]; then
    echo "CloudShell Detected, installing Java and CDK"
    # If you come back later, Java could be removed
    sudo yum -y install java-17-amazon-corretto
    # Ensure we are on latest CDK
    sudo npm install -g aws-cdk

    echo "Adding maven to path which should have been installed for CDK"
    export PATH="/home/cloudshell-user/apache-maven-3.9.5/bin:${PATH}"
fi

# Exit immediately if a command exits with a non-zero status.
set -e
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
# As long above works, attempt all other commands
set +e

pushd "ChimeCDKProvision"
echo
echo "Calling Destroy on CDK Stack to remove all Chime Voice SDK resources"
cdk destroy -c accountId=${ACCOUNT_ID} -c stackName=${CDK_STACK_NAME} -c regionEast=${regions[0]} -c regionWest=${regions[1]} --all --force
popd

# delete things in each region
for region in "${regions[@]}"; do

# Delete the stack
echo
echo "Calling SAM delete on stack in region ${region}"
sam delete --region ${region} --stack-name ${STACK_NAME} --no-prompts 

# Delete the API Keys that were pushed
echo
echo "Deleting OPENAI_API_KEY and SQUARE_API_KEY API from SSM Parameter Store in region ${region}"
aws ssm --region ${region} delete-parameters --names "/${STACK_NAME}/OPENAI_API_KEY" "/${STACK_NAME}/SQUARE_API_KEY" "/${STACK_NAME}/FB_PAGE_ACCESS_TOKEN" > /dev/null


echo
echo "Deleting Log Groups starting with /aws/lambda/${CDK_STACK_NAME} in region ${region}"
declare -a LGS=($(aws logs describe-log-groups --region ${region} --log-group-name-prefix /aws/lambda/${CDK_STACK_NAME} --query logGroups[].logGroupName --output text))
for logGroup in "${LGS[@]}"; do
echo "  Delete Log group [${logGroup}]"
aws logs delete-log-group --region ${region} --log-group-name "${logGroup}" > /dev/null
done


echo
echo "Deleting Log Groups starting with /aws/lambda/${STACK_NAME} in region ${region}"
declare -a LGS=($(aws logs describe-log-groups --region ${region} --log-group-name-prefix /aws/lambda/${STACK_NAME} --query logGroups[].logGroupName --output text))
for logGroup in "${LGS[@]}"; do
echo "  Delete Log group [${logGroup}]"
aws logs delete-log-group --region ${region} --log-group-name "${logGroup}" > /dev/null
done

done

echo
echo "All resources should be destroyed and all logs cleaned out"


