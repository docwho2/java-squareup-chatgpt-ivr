/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Reference;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsCustomResourceProps;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.PhysicalResourceIdReference;
import software.amazon.awscdk.services.iam.PolicyStatement;

/**
 *
 * @author sjensen
 */
public class ChimeSipMediaApp extends AwsCustomResource {
    
    public ChimeSipMediaApp(Stack scope, Reference lambdaArn) {
        super(scope, "SMA-CR", AwsCustomResourceProps.builder()
                .resourceType("Custom::SipMediaApplication")
                .installLatestAwsSdk(Boolean.FALSE)
                //.policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder().resources(AwsCustomResourcePolicy.ANY_RESOURCE).build()))
                // Normally just the above will work, but as part of making the chime related call it also makes lambda calls
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(PolicyStatement.Builder.create().actions(List.of("lambda:*","chime:*")).resources(List.of("*")).build())))
                .onCreate(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("CreateSipMediaApplicationCommand")
                        .physicalResourceId(PhysicalResourceId.fromResponse("SipMediaApplication.SipMediaApplicationId"))
                        .parameters(new SMAParameters(scope.getRegion(),scope.getStackName() + "-sma", List.of(new SipMediaApplicationEndpoint(lambdaArn.toString()))))
                        .build())
                .onDelete(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("DeleteSipMediaApplicationCommand")
                        .parameters(Map.of("SipMediaApplicationId", new PhysicalResourceIdReference()))
                        .build())
                .build());
    }
    
    /**
     * The ARN for the SMA that was created
     * @return 
     */
    public String getArn() {
        return getResponseField("SipMediaApplication.SipMediaApplicationArn");
    }
    
    
    private static class SMAParameters implements Serializable {

        @JsonProperty(value = "AwsRegion")
        String AwsRegion;
        
        @JsonProperty(value = "Name")
        String Name;
        
        @JsonProperty(value = "Endpoints")
        List<SipMediaApplicationEndpoint> Endpoints;

        public SMAParameters(String AwsRegion, String Name, List<SipMediaApplicationEndpoint> Endpoints) {
            this.AwsRegion = AwsRegion;
            this.Name = Name;
            this.Endpoints = Endpoints;
        }

    }

    private static class SipMediaApplicationEndpoint implements Serializable {
        @JsonProperty(value = "LambdaArn")
        String LambdaArn;

        public SipMediaApplicationEndpoint(String LambdaArn) {
            this.LambdaArn = LambdaArn;
        }
        
    }
}
