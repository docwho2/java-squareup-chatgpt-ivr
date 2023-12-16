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
public class LangBundle_es extends ListResourceBundle {

    private final static String[][] contents = {
        // Sorry, I'm having a problem fulfilling your request. Please try again later.
        {UNHANDLED_EXCEPTION.toString(), "Lo siento, tengo problemas para cumplir con tu solicitud. Por favor, inténtelo de nuevo más tarde."},
        // I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?
        {BLANK_RESPONSE.toString(), "Lo siento, no entendí eso, si terminaste, simplemente dime adiós, de lo contrario, dime cómo puedo ayudarte?"},
        // What else can I help you with?
        {ANYTHING_ELSE.toString(), "  ¿En qué más puedo ayudarte?"},
         // The operation timed out, please ask your question again
        {OPERATION_TIMED_OUT.toString(), "Se agotó el tiempo de espera de la operación, vuelva a hacer su pregunta"},
        // Response Language
        {CHATGPT_RESPONSE_LANGUAGE.toString(), "Please respond to all prompts in Spanish.  "},
         // Thank you for calling, goodbye.
        {GOODBYE.toString(), "Gracias por llamar, adiós."},
    };

    @Override
    protected Object[][] getContents() {
        return contents;
    }

}
