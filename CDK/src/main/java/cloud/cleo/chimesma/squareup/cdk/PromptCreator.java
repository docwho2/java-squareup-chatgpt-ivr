/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.CfnCustomResource;
import software.amazon.awscdk.CfnCustomResourceProps;
import software.amazon.awscdk.Stack;

import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.s3.Bucket;

/**
 *
 * @author sjensen
 */
public class PromptCreator extends SAMFunction {

    public PromptCreator(Stack stack, Bucket promptBucket) {
        super(stack, "PromptCreator");

        var code = Code.fromAsset("../ChimeSMALibrary/PollyPromptCreation/target/generate-poly-prompt-1.0.jar").bind(this).getS3Location();
        var s3Loc = S3LocationProperty.builder().bucket(code.getBucketName()).key(code.getObjectKey()).build();

        setFunctionName(stack.getStackName() + "-PromptCreator");
        setDescription("Creates Static prompts to be used in Chime Voice SDK");
        setHandler("cloud.cleo.chimesma.PollyPromptGenerator");

        setCodeUri(s3Loc);
        setEnvironment(FunctionEnvironmentProperty.builder()
                .variables(Map.of("PROMPT_BUCKET", promptBucket.getBucketName()))
                .build());
        setPolicies(List.of(
                SAMPolicyTemplateProperty.builder().s3CrudPolicy(BucketSAMPTProperty.builder().bucketName(promptBucket.getBucketName()).build()).build(),
                IAMPolicyDocumentProperty.builder()
                        //.version("2012-10-17")
                        .statement(PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("polly:SynthesizeSpeech"))
                                .resources(List.of("*"))
                                .build().toJSON())
                        .build()
        ));

    }
    
    public CfnCustomResource addPrompt(String VoiceId, String PromptName, String PromptText) {
        final var cr =  new CfnCustomResource(getStack(), PromptName, CfnCustomResourceProps.builder()
                .serviceToken(getAtt("Arn").toString())
                .build());
        
        cr.addPropertyOverride("VoiceId", VoiceId);
        cr.addPropertyOverride("PromptName", PromptName);
        cr.addPropertyOverride("PromptText", PromptText);
        return cr;
    }

}
