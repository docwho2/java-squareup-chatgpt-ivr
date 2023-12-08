/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 * @author sjensen
 */
@AllArgsConstructor
public enum LexInputMode {

    DTMF("DTMF"),
    SPEECH("Speech"),
    TEXT("Text");

    @Getter
    private final String mode;


    public static LexInputMode fromString(String mode) {
        return switch (mode) {
            case "DTMF" ->
                DTMF;
            case "Speech" ->
                SPEECH;
            case "Text" ->
                TEXT;
            default ->
                throw new RuntimeException("Unknown LexInput Mode [" + mode + "]");
        };
    }
    
}
