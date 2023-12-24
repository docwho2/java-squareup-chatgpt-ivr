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
public class LangBundle_nl extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Sorry, ik ondervind een probleem bij het voldoen aan uw verzoek. Probeer het later opnieuw."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Het spijt me, dat heb ik niet verstaan. Als je klaar bent, zeg dan gewoon gedag, vertel me anders hoe ik kan helpen?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Waar kan ik je nog meer mee helpen?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Er is een time-out opgetreden tijdens de bewerking. Stel uw vraag opnieuw"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Dutch.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Bedankt voor het bellen, tot ziens."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
