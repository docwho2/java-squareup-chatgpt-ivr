/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import java.io.UncheckedIOException;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;

/**
 *
 * @author sjensen
 */
public class DynamoConverters implements AttributeConverterProvider {

    private final Map<EnhancedType<?>, AttributeConverter<?>> converterCache = ImmutableMap.of(
            // 1. Add AddressConverter to the internal cache.
            EnhancedType.of(ChatFunctionCall.class), new ChatFunctionCallConverter());

    public static DynamoConverters create() {
        return new DynamoConverters();
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    // 2. The enhanced client queries the provider for attribute converters if it
    //    encounters a type that it does not know how to convert.
    @SuppressWarnings("unchecked")
    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        return (AttributeConverter<T>) converterCache.get(enhancedType);
    }

    // 3. Custom attribute converter
    private class ChatFunctionCallConverter implements AttributeConverter<ChatFunctionCall> {

        // 4. Transform an ChatFunctionCall object into a DynamoDB map.
        @Override
        public AttributeValue transformFrom(ChatFunctionCall cfc) {
            Map<String, AttributeValue> attributeValueMap;
            try {
                attributeValueMap = Map.of(
                        "name", AttributeValue.builder().s(cfc.getName()).build(),
                        "arguments", AttributeValue.builder().s(mapper.writeValueAsString(cfc.getArguments())).build());
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException("Unable to serialize object", e);
            }

            return AttributeValue.builder().m(attributeValueMap).build();
        }

        // 5. Transform the DynamoDB map attribute to an ChatFunctionCall oject.
        @Override
        public ChatFunctionCall transformTo(AttributeValue attributeValue) {
            Map<String, AttributeValue> m = attributeValue.m();
            ChatFunctionCall cfc = new ChatFunctionCall();
            try {
                cfc.setName(m.get("name").s());
                cfc.setArguments(mapper.readValue(m.get("arguments").s(), JsonNode.class));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException("Unable to deserialize object", e);
            }
            return cfc;
        }

        @Override
        public EnhancedType<ChatFunctionCall> type() {
            return EnhancedType.of(ChatFunctionCall.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.M;
        }
    }
}
