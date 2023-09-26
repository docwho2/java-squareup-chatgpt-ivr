/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnoreNulls;

/**
 * Our representation of a ChatGPT Message to be stored in Dynamo DB
 *
 * @author sjensen
 */
@DynamoDbBean(converterProviders = { DynamoConverters.class , DefaultAttributeConverterProvider.class} )
@NoArgsConstructor(force = true)
public class ChatGPTMessage extends ChatMessage {


    public ChatGPTMessage(MessageRole role, String content) {
        super(role.toString(), content);
    }
    
    public ChatGPTMessage(ChatMessage cm) {
        super(cm.getRole(), cm.getContent(), cm.getName(), cm.getFunctionCall());
    }

    
    @Override
    @JsonInclude() // content should always exist in the call, even if it is null
    public String getContent() {
        return super.getContent();
    }
    
    @Override
    @DynamoDbIgnoreNulls
    @JsonProperty(required = false)
    public String getName() {
        return super.getName();
    }
    
    @Override
    @JsonProperty("function_call")
    @DynamoDbAttribute(value = "function_call")
    @DynamoDbIgnoreNulls
    public ChatFunctionCall getFunctionCall() {
        return super.getFunctionCall();
    }
    
    
    public static enum MessageRole {
        user,
        system,
        assistant,
        function
    }

}
