/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cloud.cleo.squareup.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Duration;

/**
 *
 * @author sjensen
 */
public class DurationSerializer extends StdSerializer<Duration> {
    
    public DurationSerializer() {
        this(null);
    }

    public DurationSerializer(Class<Duration> t) {
        super(t);
    }

    @Override
    public void serialize(Duration t, JsonGenerator jg, SerializerProvider sp) throws IOException {
        jg.writeString(t.toString());
    }
    
}
