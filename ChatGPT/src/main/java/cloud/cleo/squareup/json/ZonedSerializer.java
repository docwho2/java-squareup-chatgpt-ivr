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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author sjensen
 */
public class ZonedSerializer extends StdSerializer<ZonedDateTime> {
    
    public ZonedSerializer() {
        this(null);
    }

    public ZonedSerializer(Class<ZonedDateTime> t) {
        super(t);
    }

    @Override
    public void serialize(ZonedDateTime t, JsonGenerator jg, SerializerProvider sp) throws IOException {
        jg.writeString(t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
    
}
