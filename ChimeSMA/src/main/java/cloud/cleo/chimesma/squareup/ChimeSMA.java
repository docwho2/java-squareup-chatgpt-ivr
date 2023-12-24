package cloud.cleo.chimesma.squareup;

import cloud.cleo.chimesma.actions.*;
import cloud.cleo.chimesma.model.ParticipantTag;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * IVR for Square Retail using Lex Bot backed by ChatGPT.
 *
 * @author sjensen
 */
public class ChimeSMA extends AbstractFlow {

    /**
     * Simple Object that caches Square data about hours to determine whether store is open or closed
     */
    private final static SquareHours squareHours = SquareHours.getInstance();

    /**
     * Main Transfer number used
     */
    private final static String MAIN_NUMBER = System.getenv("MAIN_NUMBER");
    /**
     * Voice Connector ARN so calls to main number will go SIP to PBX
     */
    private final static String VC_ARN = System.getenv("VC_ARN");

    private final static Action MAIN_MENU = getMainMenu();
    
    private final static Action ERROR_ACTION = getSystemErrorAction();

    /**
     * Initial action is to play welcome message and whether store is open or closed
     *
     * @return
     */
    @Override
    protected Action getInitialAction() {

        // Play open or closed prompt based on Square Hours  
        final var openClosed = PlayAudioAction.builder()
                .withKeyF(f -> squareHours.isOpen() ? "open.wav" : "closed.wav") // This is always in english
                .withNextAction(MAIN_MENU)
                .withErrorAction(MAIN_MENU)
                .build();

        // Start with a welcome message
        final var welcome = PlayAudioAction.builder()
                .withKey("welcome.wav") // This is always in english
                .withNextAction(openClosed)
                .withErrorAction(openClosed)
                .build();

        return welcome;
    }

    /**
     * Main menu is just a LexBox, and the only outputs are Quit and Transfer. Quit - hang up the call Transfer. -
     * transfer the call to another number.
     *
     * @return
     */
    public static Action getMainMenu() {

        final var hangup = HangupAction.builder()
                .withDescription("Normal Hangup ").build();

        // Function that passes the Calling Number to Lex
        Function<StartBotConversationAction, Map<String, String>> attributesFunction = (action) -> {
            return Map.of("callingNumber", action.getEvent().getCallDetails().getParticipants().get(0).getFrom());
        };

        // Map to Hold all all our Bots by Language
        Map<Language, StartBotConversationAction> botLangMap = new HashMap<>();

        final var lexBotEN = StartBotConversationAction.builder()
                .withDescription("ChatGPT English")
                .withLocale(Locale.forLanguageTag("en-US"))
                .withContent("You can ask about our products, hours, location, or speak to one of our team members. Tell us how we can help today?")
                .withSessionAttributesF(attributesFunction)
                .build();
        botLangMap.put(Language.English, lexBotEN);

        // Spanish
        botLangMap.put(Language.Spanish, StartBotConversationAction.builder()
                .withDescription("ChatGPT Spanish")
                .withLocale(Locale.forLanguageTag("es-US"))
                .withContent("Cuéntanos ¿cómo podemos ayudar hoy?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());

        // German
        botLangMap.put(Language.German, StartBotConversationAction.builder()
                .withDescription("ChatGPT German")
                .withLocale(Locale.forLanguageTag("de-DE"))
                .withContent("Sagen Sie uns, wie wir heute helfen können?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());

        // Finnish
        botLangMap.put(Language.Finnish, StartBotConversationAction.builder()
                .withDescription("ChatGPT Finnish")
                .withLocale(Locale.forLanguageTag("fi-FI"))
                .withContent("Kerro meille, kuinka voimme auttaa tänään?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        // French
        botLangMap.put(Language.French, StartBotConversationAction.builder()
                .withDescription("ChatGPT French")
                .withLocale(Locale.forLanguageTag("fr-CA"))
                .withContent("Dites-nous comment nous pouvons vous aider aujourd'hui ?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        // Dutch
        botLangMap.put(Language.Dutch, StartBotConversationAction.builder()
                .withDescription("ChatGPT Dutch")
                .withLocale(Locale.forLanguageTag("nl-NL"))
                .withContent("Vertel ons hoe we vandaag kunnen helpen?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        // Norwegian
        botLangMap.put(Language.Norwegian, StartBotConversationAction.builder()
                .withDescription("ChatGPT Norwegian")
                .withLocale(Locale.forLanguageTag("nb-NO"))
                .withContent("Fortell oss hvordan vi kan hjelpe i dag?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        // Polish
        botLangMap.put(Language.Polish, StartBotConversationAction.builder()
                .withDescription("ChatGPT Polish")
                .withLocale(Locale.forLanguageTag("pl-PL"))
                .withContent("Powiedz nam, jak możemy dziś pomóc?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        // Swedish
        botLangMap.put(Language.Swedish, StartBotConversationAction.builder()
                .withDescription("ChatGPT Swedish")
                .withLocale(Locale.forLanguageTag("sv-SE"))
                .withContent("Berätta för oss hur vi kan hjälpa till idag?") // Tell us how we can help today?
                .withSessionAttributesF(attributesFunction)
                .build());
        
        //
        // MOH Flow TODO
        //
        final var moh = CallAndBridgeAction.builder()
                .withDescription("Park Call with Music On Hold at PBX")
                .withCallTimeoutSeconds(10)
                .withUri("+13204952401") // Goes to Park Ext on PBX which gets you Hold Music
                .withArn(VC_ARN)
                .withNextLegBHangupAction(lexBotEN)
                .build();

        final var anyDigit = ReceiveDigitsAction.builder()
                .withInputDigitsRegex("^([0-9]|#|\\*)$")
                .withInBetweenDigitsDurationInMilliseconds(1000)
                .withFlushDigitsDurationInMilliseconds(3000)
                .withNextAction(moh)
                // If they hit a key, disconnect LEG-B which is MOH side of the call
                .withDigitsRecevedAction(HangupAction.builder().withParticipantTag(ParticipantTag.LEG_B).build())
                .build();

        // Create a Next Action handler to be shared by all the Bots
        Function<StartBotConversationAction, Action> botNextAction = (a) -> {
            final var attrs = a.getActionData().getIntentResult().getSessionState().getSessionAttributes();
            final var botResponse = attrs.get("bot_response");  // When transferring or hanging up, play back GPT's last response
            final var action = attrs.get("action");  // We don't need or want real intents, so the action when exiting the Bot will be set
            return switch (action) {
                case "transfer_call" -> {
                    final var phone = attrs.get("transfer_number");
                    final var transfer = CallAndBridgeAction.builder()
                            .withDescription("Send Call to Team Member")
                            .withRingbackToneKey("ringing.wav")
                            .withCallTimeoutSeconds(60) // Store has 40 seconds before VM, and default is 30, so push to 60 to be safe
                            .withUri(phone)
                            .build();
                    if (phone.equals(MAIN_NUMBER) && !VC_ARN.equalsIgnoreCase("PSTN")) {
                        // We are transferring to main number, so use SIP by sending call to Voice Connector
                        transfer.setArn(VC_ARN);
                        transfer.setDescription("Send Call to Main Number via SIP");
                    }
                    yield SpeakAction.builder()
                    .withDescription("Indicate transfer in progress with Bot response")
                    .withText(botResponse)
                    .withNextAction(transfer)
                    .build();
                }
                case "hold_call" ->
                    SpeakAction.builder()
                    .withDescription("Indicate MOH and press any digit to return")
                    .withText(botResponse)
                    .withNextAction(anyDigit)
                    .build();
                case "hangup_call" ->
                    SpeakAction.builder()
                    .withDescription("Saying Good Bye")
                    .withTextF(tf -> botResponse)
                    .withNextAction(hangup)
                    .build();
                case "switch_language" -> {
                    // Obtain the bot locale based on the language attribute from the session
                    final var bot = botLangMap.getOrDefault(Language.valueOf(attrs.get("language")), lexBotEN);
                    // Start bot in that language with GPT response (which will be in the target language)
                    bot.setContentF(f -> botResponse);
                    yield bot;
                }
                default ->
                    ERROR_ACTION;
            };
        };

        // All Bots regardless of language will use the next action handler above
        botLangMap.values().forEach(bot -> bot.setNextActionF(botNextAction));

        // We will start in English and GPT will detect and call back to us to switch languages as necessary
        return lexBotEN;
    }

    /**
     * When an error occurs on a Action and the Action did not specify an Error Action
     *
     * @return the default error Action
     */
    @Override

    protected Action getErrorAction() {
        return ERROR_ACTION;
    }
    
    private static Action getSystemErrorAction() {
        final var hangup = HangupAction.builder()
                .withDescription("System error Hangup ").build();

        final var goodbye = PlayAudioAction.builder()
                .withDescription("System Error Say Goodbye")
                .withKeyLocale("goodbye")
                .withNextAction(hangup)
                .build();
        
        final var errMsg = PlayAudioAction.builder()
                .withDescription("System Error Message")
                .withKeyLocale("error")
                .withNextAction(goodbye)
                .build();

        return errMsg;
    }

    @Override
    protected void newCallHandler(Action action) {
        log.info("New Call Handler Code Here");
    }

    @Override
    protected void hangupHandler(Action action) {
        log.info("Hangup Handler Code Here");
    }

    /**
     * Voice Languages we support (that are built out in Lex)
     */
    static enum Language {
        English,
        Spanish,
        German,
        Finnish,
        French,
        Dutch,
        Norwegian,
        Polish,
        Swedish;
    }

}
