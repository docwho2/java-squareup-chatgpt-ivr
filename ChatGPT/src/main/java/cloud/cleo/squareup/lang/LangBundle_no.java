/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cloud.cleo.squareup.lang;


import static cloud.cleo.squareup.lang.LangUtil.LanguageIds.*;
import java.util.ListResourceBundle;

/**
 * English Strings
 *
 * @author sjensen
 */
public class LangBundle_no extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Beklager, jeg har problemer med å oppfylle forespørselen din. Prøv igjen senere."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Beklager, jeg fikk ikke med meg det. Hvis du er ferdig, si farvel, ellers fortell meg hvordan jeg kan hjelpe?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Hva annet kan jeg hjelpe deg med?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Operasjonen ble tidsavbrutt. Still spørsmålet ditt igjen"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Norwegian.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Takk for at du ringte, farvel."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
