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
public class LangBundle_fr extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Désolé, j'ai un problème pour répondre à votre demande. Veuillez réessayer plus tard."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Je suis désolé, je n'ai pas compris, si vous avez terminé, dites simplement au revoir, sinon dites-moi comment je peux vous aider ?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  Pour quoi d'autre puis-je vous aider ?"},
        // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "L'opération a expiré, veuillez poser à nouveau votre question"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in French.  "},
        // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Merci d'avoir appelé, au revoir."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
