package cloud.cleo.chimesma.squareup;

import cloud.cleo.chimesma.actions.*;
import cloud.cleo.chimesma.model.ParticipantTag;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * IVR for Square Retail using Lex Bot backed by ChatGPT.
 *
 * @author sjensen
 */
public class ChimeSMA extends AbstractFlow {

    private final static Action MAIN_MENU = getMainMenu();

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

        final var english = Locale.forLanguageTag("en-US");
        final var spanish = Locale.forLanguageTag("es-US");

        final var hangup = HangupAction.builder()
                .withDescription("This is my last step").build();

        final var goodbye = PlayAudioAction.builder()
                .withDescription("Say Goodbye")
                .withKeyLocale("goodbye")
                .withNextAction(hangup)
                .build();

        final var lexBotEN = StartBotConversationAction.builder()
                .withDescription("ChatGPT English")
                .withLocale(english)
                .withContent("You can ask about our products, hours, location, or speak to one of our team members. Tell us how we can help today?")
                // Send the calling number in so we can send texts if need be
                .withSessionAttributesF(action -> Map.of("calling_number", action.getEvent().getCallDetails().getParticipants().get(0).getFrom()))
                .build();

        // Will add Spanish later if needed
        final var lexBotES = StartBotConversationAction.builder()
                .withDescription("ChatGPT Spanish")
                .withLocale(spanish)
                .withContent("¿En qué puede ayudarte Chat GPT?")
                // Send the calling number in so we can send texts if need be
                .withSessionAttributesF(action -> Map.of("calling_number", action.getEvent().getCallDetails().getParticipants().get(0).getFrom()))
                .build();

        //
        // MOH Flow
        //
        final var moh = CallAndBridgeAction.builder()
                .withDescription("Send Call to Music On Hold")
                .withCallTimeoutSeconds(10)
                .withUri("+13204952401") // Goes to Park Ext on PBX which gets you Hold Music
                .withArn(VC_ARN)
                .withNextLegBHangupAction(lexBotEN)
                .build();
        moh.setArn(VC_ARN);
        log.debug("VC_ARN = " + VC_ARN);
        final var anyDigit = ReceiveDigitsAction.builder()
                .withInputDigitsRegex("^([0-9]|#|\\*)$")
                .withInBetweenDigitsDurationInMilliseconds(1000)
                .withFlushDigitsDurationInMilliseconds(3000)
                .withNextAction(moh)
                // If they hit a key, disconnect LEG-B which is MOH side of the call
                .withDigitsRecevedAction(HangupAction.builder().withParticipantTag(ParticipantTag.LEG_B).build())
                .build();

        // Two invocations of the bot, so create one function and use for both
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
                    .withDescription("Indicate MOH and press any key to return")
                    .withText(botResponse)
                    .withNextAction(anyDigit)
                    .build();
                case "hangup_call" ->
                    SpeakAction.builder()
                    .withDescription("Saying Good Bye")
                    .withTextF(tf -> botResponse)
                    .withNextAction(hangup)
                    .build();
                default ->
                    SpeakAction.builder()
                    .withText("A system error has occured, please call back and try again")
                    .withNextAction(hangup)
                    .build();
            };
        };

        // Both bots are the same, so the handler is the same
        lexBotEN.setNextActionF(botNextAction);
        lexBotES.setNextActionF(botNextAction);

        return lexBotEN;
    }

    /**
     * When an error occurs on a Action and the Action did not specify an Error Action
     *
     * @return the default error Action
     */
    @Override
    protected Action getErrorAction() {
        final var errMsg = SpeakAction.builder()
                .withText("A system error has occured, please call back and try again")
                .withNextAction(new HangupAction())
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

}
