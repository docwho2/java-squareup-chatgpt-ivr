# Common stuff between deploy and destroy scripts

# Stack name for the Chime resources provisioned by CDK
CDK_STACK_NAME=dude-cli-cdk

# Stack name for the SMA general deployment
STACK_NAME=dude-cli

# Regions we will deploy to (the only supported US regions for Chime PSTN SDK)
declare -a regions=( us-east-1 us-west-2)
