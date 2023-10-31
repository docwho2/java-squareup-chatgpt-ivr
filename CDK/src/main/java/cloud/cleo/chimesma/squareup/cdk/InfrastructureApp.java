package cloud.cleo.chimesma.squareup.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public final class InfrastructureApp {

    public static void main(final String[] args) {
        final var app = new App();

        final var stack = new InfrastructureStack(app, null, StackProps.builder()
                .description("Killer Stack that provisions Chime Voice SDK resources")
                .stackName("squareup-chime-cdk")
                .build());

        app.synth();

    }
}
