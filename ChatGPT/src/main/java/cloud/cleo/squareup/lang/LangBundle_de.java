/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cloud.cleo.squareup.lang;


import static cloud.cleo.squareup.lang.LangUtil.LanguageIds.*;
import java.util.ListResourceBundle;

/**
 * Spanish Strings
 *
 * @author sjensen
 */
public class LangBundle_de extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Es tut mir leid, ich habe ein Problem bei der Erfüllung Ihrer Anfrage. Bitte versuchen Sie es später noch einmal."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Es tut mir leid, ich habe das nicht verstanden. Wenn Sie fertig sind, verabschieden Sie sich einfach, sonst sagen Sie mir, wie ich helfen kann?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Wobei kann ich Ihnen sonst noch helfen?"},
         // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Beim Vorgang ist eine Zeitüberschreitung aufgetreten. Bitte stellen Sie Ihre Frage erneut"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in German.  "},
         // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Danke für Ihren Anruf, auf Wiedersehen."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
