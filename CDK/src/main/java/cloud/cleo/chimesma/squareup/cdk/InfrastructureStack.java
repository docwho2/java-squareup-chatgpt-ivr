package cloud.cleo.chimesma.squareup.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.sam.CfnFunction;
import software.amazon.awscdk.services.sam.CfnFunctionProps;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.ssm.StringParameterProps;

/**
 * CDK Stack
 *
 * @author sjensen
 */
public class InfrastructureStack extends Stack {

    public InfrastructureStack(final App parent, final String id) {
        this(parent, id, null);
    }

    public InfrastructureStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        // Dummy Lambda so we can create SMA
        CfnFunction lambda = new CfnFunction(this, "dummy-function", CfnFunctionProps.builder()
                .inlineCode("exports.handler = async (event) => {console.log(event)};")
                .handler("index.handler")
                .runtime(Runtime.NODEJS_LATEST.getName())
                .build());

        ChimeSipMediaApp sma = new ChimeSipMediaApp(this, lambda.getAtt("Arn"));

        ChimeVoiceConnector vc = new ChimeVoiceConnector(this);

        ChimeSipRule sr = new ChimeSipRule(this, vc, sma);
        
        new StringParameter(this, "SMA_ID_PARAM" , StringParameterProps.builder()
                .parameterName("/" + getStackName() + "/SMA_ID")
                .description("The ID for the Session Media App (SMA)")
                .stringValue(sma.getSMAId())
                .build());

        new CfnOutput(this, "SMA_ID", CfnOutputProps.builder()
                .description("The ID for the Session Media App (SMA)")
                .value(sma.getSMAId())
                .build());
        
        new CfnOutput(this, "sma-arn", CfnOutputProps.builder()
                .description("The ARN for the Session Media App (SMA)")
                .value(sma.getArn())
                .build());

        new CfnOutput(this, "vc-hostname", CfnOutputProps.builder()
                .description("The Hostname for the Voice Connector")
                .value(vc.getOutboundName())
                .build());
    }

}
