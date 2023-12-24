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
public class LangBundle_pl extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Przepraszamy, mam problem z realizacją Twojej prośby. Spróbuj ponownie później."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Przepraszam, nie zrozumiałem. Jeśli już skończyłeś, po prostu się pożegnaj. W przeciwnym razie powiedz mi, jak mogę pomóc?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  W czym jeszcze mogę Ci pomóc?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Upłynął limit czasu operacji. Zadaj pytanie ponownie"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Polish.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Dziękuję za telefon, do widzenia."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
