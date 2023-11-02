package cloud.cleo.chimesma.squareup.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public final class InfrastructureApp {

    private final static String STACK_DESC = "Provision Chime Voice SDK resources (VoiceConnector, SIP Rule, SIP Media App)";
    
    public static void main(final String[] args) {
        final var app = new App();

        String accountId = (String) app.getNode().tryGetContext("accountId");
        requireNonEmpty(accountId, "accountId is required via -c parameter to cdk");
        
        String stackName = (String) app.getNode().tryGetContext("stackName");
        if ( stackName == null || stackName.isBlank() ) {
            stackName = "squareup-chime-cdk";
        }

        final var stack_east = new InfrastructureStack(app, "east", StackProps.builder()
                .description(STACK_DESC)
                .stackName(stackName)
                .env(makeEnv(accountId, "us-east-1"))
                .build());
        
        final var stack_west = new InfrastructureStack(app, "west", StackProps.builder()
                .description(STACK_DESC)
                .stackName(stackName)
                .env(makeEnv(accountId, "us-west-2"))
                .build());

        app.synth();

    }

    static Environment makeEnv(String accountId, String region) {
        return Environment.builder()
                .account(accountId)
                .region(region)
                .build();
    }

    static void requireNonEmpty(String string, String message) {
        if (string == null || string.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
