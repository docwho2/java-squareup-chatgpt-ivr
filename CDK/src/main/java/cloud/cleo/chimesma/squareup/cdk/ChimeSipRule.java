/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsCustomResourceProps;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.PhysicalResourceIdReference;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;

/**
 *
 * @author sjensen
 */
public class ChimeSipRule extends AwsCustomResource {

    private final static String ID = "SR-CR";

    /**
     * The SIP Rule ID in the API response
     */
    private final static String SR_ID = "SipRule.SipRuleId";

    public ChimeSipRule(Stack scope, ChimeVoiceConnector vc, ChimeSipMediaApp sma) {
        super(scope, ID, AwsCustomResourceProps.builder()
                .resourceType("Custom::SipRule")
                .installLatestAwsSdk(Boolean.FALSE)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder().resources(AwsCustomResourcePolicy.ANY_RESOURCE).build()))
                .onCreate(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("CreateSipRuleCommand")
                        .physicalResourceId(PhysicalResourceId.fromResponse(SR_ID))
                        .parameters(Map.of("Name", scope.getStackName() + "-" + scope.getRegion(),
                                "TriggerType", "RequestUriHostname",
                                "TriggerValue",vc.getOutboundName(),
                                "Disabled", false,
                                "TargetApplications", List.of(Map.of("SipMediaApplicationId", sma.getSMAId(),"Priority",1,"AwsRegion",scope.getRegion()))
                        ))
                        .build())
                .onDelete(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("DeleteSipRuleCommand")
                        .parameters(Map.of("SipRuleId", new PhysicalResourceIdReference()))
                        .build())
                .build());

    }

}
