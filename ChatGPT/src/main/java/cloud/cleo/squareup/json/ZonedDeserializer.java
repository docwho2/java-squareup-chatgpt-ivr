/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cloud.cleo.squareup.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author sjensen
 */
public class ZonedDeserializer extends StdDeserializer<ZonedDateTime> {

    public ZonedDeserializer() {
        this(null);
    }

    public ZonedDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ZonedDateTime deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        System.out.println("Called to deserialze " + jp.getText());
        return ZonedDateTime.parse(jp.getText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
