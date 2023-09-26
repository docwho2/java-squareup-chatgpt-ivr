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
import java.time.ZoneId;

/**
 *
 * @author sjensen
 */
public class TimezoneSerializer extends StdSerializer<ZoneId> {
    
    public TimezoneSerializer() {
        this(null);
    }

    public TimezoneSerializer(Class<ZoneId> t) {
        super(t);
    }

    @Override
    public void serialize(ZoneId t, JsonGenerator jg, SerializerProvider sp) throws IOException {
        jg.writeString(t.toString());
    }
    
}
