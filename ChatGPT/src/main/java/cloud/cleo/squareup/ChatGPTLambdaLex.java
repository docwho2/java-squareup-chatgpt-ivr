package cloud.cleo.squareup;

import static cloud.cleo.squareup.ChatGPTLambda.mapper;
import static cloud.cleo.squareup.lang.LangUtil.LanguageIds.UNHANDLED_EXCEPTION;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.concurrent.CompletionException;





/**
 * Process incoming Lex Events
 * 
 *  
 * @author sjensen
 */
public class ChatGPTLambdaLex extends ChatGPTLambda implements RequestHandler<LexV2Event, LexV2Response> {

    
    @Override
    public LexV2Response handleRequest(LexV2Event lexRequest, Context cntxt) {
        // Wrapped Event Class
        final LexV2EventWrapper event = new LexV2EventWrapper(lexRequest);
        try {
            log.debug(mapper.valueToTree(lexRequest).toPrettyString());
            // Intent which doesn't matter for us
            log.debug("Intent: " + event.getIntent());

            // For this use case, we only ever get the FallBack Intent, so the intent name means nothing here
            // We will process everythiung coming in as text to pass to GPT
            // IE, we are only using lex here to process speech and send it to us
            return switch (event.getIntent()) {
                default ->
                    processGPT(event);
            };

        } catch (CompletionException e) {
            log.error("Unhandled Future Exception", e.getCause());
            return buildResponse(new LexV2EventWrapper(lexRequest), event.getLangString(UNHANDLED_EXCEPTION));
        } catch (Exception e) {
            log.error("Unhandled Exception", e);
            // Unhandled Exception
            return buildResponse(new LexV2EventWrapper(lexRequest), event.getLangString(UNHANDLED_EXCEPTION));
        }
    }


   
}
