package cloud.cleo.chimesma.squareup.cdk;

import java.util.List;
import software.amazon.awscdk.App;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.CfnAuthorizer;
import software.amazon.awscdk.services.apigatewayv2.CfnAuthorizerProps;
import software.amazon.awscdk.services.apigatewayv2.CfnRoute;
import software.amazon.awscdk.services.apigatewayv2.CfnRouteProps;
import software.amazon.awscdk.services.sam.CfnHttpApi;
import software.amazon.awscdk.services.sam.CfnHttpApiProps;

/**
 * Just testing SAM HTTP API_GW with authorizer
 * @author sjensen
 */
public class HttpStack extends Stack {

    public HttpStack(final App parent, final String id) {
        this(parent, id, null);
    }

    public HttpStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        CfnHttpApi httpApi = new CfnHttpApi(this, "sample-api", CfnHttpApiProps.builder()
                .description("sample-api")
                .disableExecuteApiEndpoint(Boolean.TRUE)
                .corsConfiguration(Boolean.TRUE)
                .build());

        CfnAuthorizer auth = new CfnAuthorizer(this, "jwt-auth", CfnAuthorizerProps.builder()
                .apiId(httpApi.getRef())
                .authorizerType("JWT")
                .identitySource(List.of("$request.header.Authorization"))
                .name("JWTDUDE")
                .jwtConfiguration(CfnAuthorizer.JWTConfigurationProperty.builder().
                        audience(List.of("https://jwt.io")).issuer("https://blueshirtlogon-qa.bestbuy.com").build())
                .build());

        CfnRoute route = new CfnRoute(this, "route-1", CfnRouteProps.builder()
                .apiId(httpApi.getRef())
                .routeKey("GET /")
                .authorizationType("JWT")
                .authorizerId(auth.getRef())
                .build());


        new CfnOutput(this, "HttApi", CfnOutputProps.builder()
                .description("Url for Http Api")
                .value(httpApi.getAtt("ApiEndpoint").toString())
                .build());
    }

}
