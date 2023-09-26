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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author sjensen
 */
public class LocalTimeSerializer extends StdSerializer<LocalTime> {
    
    public LocalTimeSerializer() {
        this(null);
    }

    public LocalTimeSerializer(Class<LocalTime> t) {
        super(t);
    }

    @Override
    public void serialize(LocalTime t, JsonGenerator jg, SerializerProvider sp) throws IOException {
        jg.writeString(t.format(DateTimeFormatter.ISO_LOCAL_TIME));
    }
    
}
