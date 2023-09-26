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
import java.time.ZoneId;


/**
 *
 * @author sjensen
 */
public class ZoneIdDeserializer extends StdDeserializer<ZoneId> {

    public ZoneIdDeserializer() {
        this(null);
    }

    public ZoneIdDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ZoneId deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        System.out.println("Called to deserialze " + jp.getText());
        return ZoneId.of(jp.getText());
    }

}
