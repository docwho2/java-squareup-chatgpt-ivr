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
public class LangBundle_sv extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Tyvärr, jag har problem med att uppfylla din begäran. Vänligen försök igen senare."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Jag är ledsen, jag fattade inte det, om du är klar, säg bara hejdå, annars berätta för mig hur jag kan hjälpa?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Vad mer kan jag hjälpa dig med?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Åtgärden tog timeout, ställ din fråga igen"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Swedish.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Tack för att du ringde, hejdå."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
