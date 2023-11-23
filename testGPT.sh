#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Stack names and regions
source config.sh

# use today's date as the session ID so you can keep a conversation going
current_date=$(date +%Y-%m-%d)

echo
echo
default_value="How are you doing today?"
read -p "Enter text to send to GPT Bot [${default_value}]: " CHAT
CHAT=${CHAT:-$default_value}

# query in each region
for region in "${regions[@]}"; do

BOT_ID=$(aws ssm get-parameter --region ${region} --name /${STACK_NAME}/BOT_ID --query Parameter.Value --output text)
BOT_ALIAS_ID=$(aws ssm get-parameter --region ${region} --name /${STACK_NAME}/BOT_ALIAS_ID --query Parameter.Value --output text)

RESULT=$(aws lexv2-runtime recognize-text --text "${CHAT}" --region ${region} \
--output text --query 'messages[-1:].content' --bot-id ${BOT_ID} --bot-alias-id ${BOT_ALIAS_ID} \
--locale-id en_US --session-id ${current_date})

echo
echo -e "[\033[36m${CHAT}\033[0m] - ${region}"
echo "Response is: "
echo -e "[\033[34m${RESULT}\033[0m]"
echo
done