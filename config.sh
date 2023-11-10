# Common stuff between deploy and destroy scripts

# Stack name for the SMA general deployment
STACK_NAME=chatgpt-square-ivr

# Stack name for the Chime resources provisioned by CDK
CDK_STACK_NAME=${STACK_NAME}-cdk

# Regions we will deploy to (the only supported US regions for Chime PSTN SDK)
declare -a regions=( us-east-1 us-west-2)
