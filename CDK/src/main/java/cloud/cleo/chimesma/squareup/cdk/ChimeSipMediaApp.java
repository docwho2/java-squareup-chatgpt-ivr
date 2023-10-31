/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import software.amazon.awscdk.Reference;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsCustomResourceProps;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.PhysicalResourceIdReference;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.CfnPermission;
import software.amazon.awscdk.services.lambda.CfnPermissionProps;

/**
 *
 * @author sjensen
 */
public class ChimeSipMediaApp extends AwsCustomResource {
    
    private final static String ID = "SMA-CR";
    private final static String ID_PERM = ID + "-PERM";
    
    /**
     * The SMA ID in the API response
     */
    private final static String SMA_ID = "SipMediaApplication.SipMediaApplicationId";
    private final static String SMA_ARN = "SipMediaApplication.SipMediaApplicationArn";
    
    public ChimeSipMediaApp(Stack scope, Reference lambdaArn) {
        super(scope, ID, AwsCustomResourceProps.builder()
                .resourceType("Custom::SipMediaApplication")
                .installLatestAwsSdk(Boolean.FALSE)
                //.policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder().resources(AwsCustomResourcePolicy.ANY_RESOURCE).build()))
                // Normally just the above will work, but as part of making the chime related call it also makes lambda calls
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(PolicyStatement.Builder.create().actions(List.of("lambda:*","chime:*")).resources(List.of("*")).build())))
                .onCreate(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("CreateSipMediaApplicationCommand")
                        .physicalResourceId(PhysicalResourceId.fromResponse(SMA_ID))
                        .parameters(new SMAParameters(scope.getRegion(),scope.getStackName() + "-sma", List.of(new SipMediaApplicationEndpoint(lambdaArn.toString()))))
                        .build())
                .onDelete(AwsSdkCall.builder()
                        .service("@aws-sdk/client-chime-sdk-voice")
                        .action("DeleteSipMediaApplicationCommand")
                        .parameters(Map.of("SipMediaApplicationId", new PhysicalResourceIdReference()))
                        .build())
                .build());
        
        // Add permission for Chime to Call the Lambda
        final var perm = new CfnPermission(scope, ID_PERM, CfnPermissionProps.builder()
                .functionName(lambdaArn.toString())
                .action("lambda:InvokeFunction")
                .principal("voiceconnector.chime.amazonaws.com")
                .sourceAccount(scope.getAccount())
                .sourceArn(getResponseFieldReference(SMA_ARN).toString())
                .build());
    }
    
    /**
     * The ARN for the SMA that was created
     * @return 
     */
    public String getArn() {
        return getResponseField(SMA_ARN);
    }
    
    /**
     * Required parameters for the CreateSipMediaApplicationCommand API call
     */
    @AllArgsConstructor
    private static class SMAParameters {

        @JsonProperty(value = "AwsRegion")
        String awsRegion;
        
        @JsonProperty(value = "Name")
        String name;
        
        @JsonProperty(value = "Endpoints")
        List<SipMediaApplicationEndpoint> endpoints;

    }

    @AllArgsConstructor
    private static class SipMediaApplicationEndpoint {
        @JsonProperty(value = "LambdaArn")
        String lambdaArn;
    }
}
