#!/bin/bash

aws chime update-sip-media-application --sip-media-application-id cf3e17cd-f4e5-44c3-ab04-325e6b3a6709 --endpoints LambdaArn=arn:aws:lambda:us-east-1:364253738352:function:chime-voicesdk-sma-ChatGPT --profile CLEO

aws chime update-sip-media-application --sip-media-application-id cf3e17cd-f4e5-44c3-ab04-325e6b3a6709 --endpoints LambdaArn=arn:aws:lambda:us-east-1:364253738352:function:chime-voicesdk-sma-ChimeSMA:SNAPSTART --profile CLEO


aws chime update-sip-media-application --sip-media-application-id f6fb2553-e7e0-4900-866b-1b51b91f575a --endpoints LambdaArn=arn:aws:lambda:us-west-2:364253738352:function:chime-voicesdk-sma-ChatGPT --profile CLEO --region us-west-2

aws chime update-sip-media-application --sip-media-application-id f6fb2553-e7e0-4900-866b-1b51b91f575a --endpoints LambdaArn=arn:aws:lambda:us-west-2:364253738352:function:chime-voicesdk-sma-ChimeSMA:SNAPSTART --profile CLEO --region us-west-2
