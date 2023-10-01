#!/bin/bash

aws chime update-sip-media-application --sip-media-application-id d95bf7c0-6ae3-436f-9831-c5c362884b97 --endpoints LambdaArn=arn:aws:lambda:us-east-1:364253738352:function:squareup-chatgpt-ivr-ChatGPT --profile CLEO

aws chime update-sip-media-application --sip-media-application-id d95bf7c0-6ae3-436f-9831-c5c362884b97 --endpoints LambdaArn=arn:aws:lambda:us-east-1:364253738352:function:squareup-chatgpt-ivr-ChimeSMA:SNAPSTART --profile CLEO


aws chime update-sip-media-application --sip-media-application-id 14f36d6f-4ecb-4629-ac87-bc11a04b5a35 --endpoints LambdaArn=arn:aws:lambda:us-west-2:364253738352:function:squareup-chatgpt-ivr-ChatGPT --profile CLEO --region us-west-2

aws chime update-sip-media-application --sip-media-application-id 14f36d6f-4ecb-4629-ac87-bc11a04b5a35 --endpoints LambdaArn=arn:aws:lambda:us-west-2:364253738352:function:squareup-chatgpt-ivr-ChimeSMA:SNAPSTART --profile CLEO --region us-west-2
