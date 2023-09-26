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
import java.time.Duration;


/**
 *
 * @author sjensen
 */
public class DurationDeserializer extends StdDeserializer<Duration> {

    public DurationDeserializer() {
        this(null);
    }

    public DurationDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Duration deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        System.out.println("Called to deserialze " + jp.getText());
        return Duration.parse(jp.getText());
    }

}
