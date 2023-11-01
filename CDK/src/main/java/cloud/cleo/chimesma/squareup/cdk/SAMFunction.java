/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup.cdk;

import java.util.List;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.sam.CfnFunction;
import software.amazon.awscdk.services.sam.CfnFunctionProps;

/**
 * Base SAM Lambda
 * 
 * @author sjensen
 */
public class SAMFunction extends CfnFunction {
    
    public SAMFunction(Stack scope, String id) {
        super(scope, id, CfnFunctionProps.builder()
                .runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_17.getName())
                .timeout(30)
                .memorySize(3009)
                .architectures(List.of("x86_64"))
                .build());
    }
    
}
