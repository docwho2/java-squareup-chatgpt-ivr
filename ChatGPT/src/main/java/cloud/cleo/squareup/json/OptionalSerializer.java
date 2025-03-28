/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Optional;

/**
 *
 * @author sjensen
 */
public class OptionalSerializer extends StdSerializer<Optional<?>> {

    public OptionalSerializer() {
        super((Class<Optional<?>>) (Class<?>) Optional.class);
    }

    @Override
    public void serialize(Optional<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value.isPresent()) {
            // Delegate serialization of the inner value to Jackson
            provider.defaultSerializeValue(value.get(), gen);
        } else {
            // Write JSON null if the Optional is empty
            gen.writeNull();
        }
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        if (visitor != null) {
            // Since Optional can wrap any type, we indicate a general format.
            visitor.expectAnyFormat(typeHint);
        }
    }
}
