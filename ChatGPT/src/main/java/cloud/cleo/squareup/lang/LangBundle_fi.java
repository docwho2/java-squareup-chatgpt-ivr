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
public class LangBundle_fi extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Anteeksi, minulla on ongelma pyyntösi täyttämisessä. Yritä uudelleen myöhemmin."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Anteeksi, en tajunnut sitä. Jos olet tehnyt, sano vain hyvästit, muuten kerro kuinka voin auttaa?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Mitä muuta voin auttaa?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Toiminto aikakatkaistiin. Esitä kysymyksesi uudelleen"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Finnish.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Kiitos soitosta, näkemiin."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
