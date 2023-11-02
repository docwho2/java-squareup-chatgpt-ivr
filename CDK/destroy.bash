#!/bin/bash

ACCOUNT_ID=`aws sts get-caller-identity --query Account --output text`

cdk destroy -c accountId=${ACCOUNT_ID} --all --concurrency=5 --force
