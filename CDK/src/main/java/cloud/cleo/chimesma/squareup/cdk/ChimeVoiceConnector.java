/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
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
public class ChimeVoiceConnector extends AwsCustomResource {

    private final static String ID = "VC-CR";

    /**
     * The Voice Connector ID in the API response
     */
    private final static String VC_ID = "VoiceConnector.VoiceConnectorId";
    private final static String VC_ARN = "VoiceConnector.VoiceConnectorArn";

    public ChimeVoiceConnector(Stack scope) {
        super(scope, ID, AwsCustomResourceProps.builder()
                .resourceType("Custom::VoiceConnector")
                .installLatestAwsSdk(Boolean.FALSE)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder().resources(AwsCustomResourcePolicy.ANY_RESOURCE).build()))
                .onCreate(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("CreateVoiceConnectorCommand")
                        .physicalResourceId(PhysicalResourceId.fromResponse(VC_ID))
                        .parameters(new VCParameters(scope.getRegion(), scope.getStackName() + "-vc", false))
                        .build())
                .onDelete(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("DeleteVoiceConnectorCommand")
                        .parameters(Map.of("VoiceConnectorId", new PhysicalResourceIdReference()))
                        .build())
                .build());

    }

    /**
     * The ARN for the VoiceConnector that was created
     *
     * @return
     */
    public String getArn() {
        return getResponseField(VC_ARN);
    }
    
    public String getOutboundName() {
        return getResponseField("VoiceConnector.OutboundHostName");
    }

    /**
     * Required parameters for the CreateVoiceConnectorCommand API call
     */
    @AllArgsConstructor
    private static class VCParameters  {

        @JsonProperty(value = "AwsRegion")
        String awsRegion;

        @JsonProperty(value = "Name")
        String name;

        @JsonProperty(value = "RequireEncryption")
        Boolean requireEncryption;

    }

}
