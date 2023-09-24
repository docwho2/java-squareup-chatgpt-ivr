/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.chimesma.squareup;

import cloud.cleo.chimesma.actions.*;
import cloud.cleo.chimesma.actions.PlayAudioAndGetDigitsAction.AudioSourceLocale;
import cloud.cleo.chimesma.model.ParticipantTag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Example Flow that exercises many of the SMA library Actions
 *
 * @author sjensen
 */
public class ChimeSMA extends AbstractFlow {

    private final static Action MAIN_MENU = getMainMenu();
    private final static Action CALL_RECORDING_MENU = getCallRecordingMenu();

    @Override
    protected Action getInitialAction() {

        // Start with a welcome message and then main menu with region static prompt
        return PlayAudioAction.builder()
                .withKey(System.getenv("AWS_REGION") + "-welcome.wav") // This is always in english
                .withNextAction(MAIN_MENU)
                .withErrorAction(MAIN_MENU)
                .build();

    }

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

        // Send call to Connect to demo Take Back and Transfer
        final var connect = CallAndBridgeAction.builder()
                .withDescription("Send Call to AWS Connect")
                .withUri("+15052162949")
                .withRingbackToneKeyLocale("transfer")
                .build();

        final var lexBotEN = StartBotConversationAction.builder()
                .withDescription("ChatGPT English")
                .withLocale(english)
                .withContent("What can Chat GPT help you with?")
                .build();

        final var lexBotES = StartBotConversationAction.builder()
                .withDescription("ChatGPT Spanish")
                .withLocale(spanish)
                .withContent("¿En qué puede ayudarte Chat GPT?")
                .build();

        // Two invocations of the bot, so create one function and use for both
        Function<StartBotConversationAction, Action> botNextAction = (a) -> {
            switch (a.getIntentName()) {
                // The Lex bot also has intent to speak with someone
                case "Transfer":
                    return connect;
                case "Quit":
                default:
                    return MAIN_MENU;
            }
        };

        // Both bots are the same, so the handler is the same
        lexBotEN.setNextActionF(botNextAction);
        lexBotES.setNextActionF(botNextAction);

        // Main menu will be locale specific prompting
        final var menu = PlayAudioAndGetDigitsAction.builder()
                .withAudioSource(AudioSourceLocale.builder().withKeyLocale("main-menu").build())
                .withFailureAudioSource(AudioSourceLocale.builder().withKeyLocale("try-again").build())
                .withRepeatDurationInMilliseconds(3000)
                .withRepeat(2)
                .withMinNumberOfDigits(1)
                .withMaxNumberOfDigits(1)
                .withInputDigitsRegex("^\\d{1}$")
                .withErrorAction(goodbye)
                .withNextActionF(a -> {
                    switch (a.getReceivedDigits()) {
                        case "1":
                            return lexBotEN;
                        case "2":
                            return lexBotES;
                        case "3":
                            return connect;
                        case "4":
                            return CALL_RECORDING_MENU;
                        default:
                            return goodbye;
                    }
                })
                .build();

        return menu;
    }

    /**
     * Flow for call recording operations (only in English)
     *
     * @return
     */
    public static Action getCallRecordingMenu() {

        // This menu is just in English, we  will use Speak instead of static prompts like main menu
        final var menu = SpeakAndGetDigitsAction.builder()
                .withSpeechParameters(SpeakAndGetDigitsAction.SpeechParameters.builder()
                        // Static text, but this could be dyanimc as well
                        .withText("Call Recording Menu. "
                                + "Press One to re cord an Audio File. "
                                + "Press Two to Listen to your recorded Audio File. "
                                + "Any other key to return to the Main Menu").build())
                .withFailureSpeechParameters(SpeakAndGetDigitsAction.SpeechParameters.builder()
                        .withText("Plese try again").build())
                .withRepeatDurationInMilliseconds(3000)
                .withRepeat(2)
                .withMinNumberOfDigits(1)
                .withMaxNumberOfDigits(1)
                .withInputDigitsRegex("^\\d{1}$")
                .withErrorAction(MAIN_MENU)
                .build();

        final var error = SpeakAction.builder()
                .withText("Recording actions error, restarting menu")
                .withNextAction(menu)
                .build();

        final var speakStart = SpeakAction.builder()
                .withText("Call Recording has started")
                .withNextAction(menu)
                .withErrorAction(menu)
                .build();

        final var startRecording = StartCallRecordingAction.builder()
                .withStoreLocation(Boolean.TRUE)
                .withNextAction(speakStart)
                .withErrorAction(menu)
                .build();

        final var speakStop = SpeakAction.builder()
                .withText("Call Recording has stopped")
                .withNextAction(menu)
                .build();

        final var stopRecording = StopCallRecordingAction.builder()
                .withNextAction(speakStop)
                .withErrorAction(menu)
                .build();

        final var recordAudio = RecordAudioAction.builder()
                .withDurationInSeconds(30)
                .withSilenceDurationInSeconds(5)
                .withRecordingTerminators(List.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*'))
                .withNextAction(menu)
                .withErrorAction(error)
                .build();

        final var beep = PlayAudioAction.builder()
                .withKey("beep.wav")
                .withNextAction(recordAudio)
                .withErrorAction(error)
                .build();

        final var recordPrompt = SpeakAction.builder()
                .withText("At the beep, re cord up to 30 seconds of Audio.  Press any key to stop the recording.")
                .withNextAction(beep)
                .withErrorAction(error)
                .build();

        final var playAudio = PlayAudioAction.builder()
                .withBucketName(System.getenv("RECORD_BUCKET")) // We are playing from the record bucket, not default Prompting Bucket
                .withKeyF(a -> a.getTransactionAttribute(RecordAudioAction.RECORD_AUDIO_KEY).toString())
                .withErrorAction(error)
                .withNextAction(menu)
                .build();

        final var noRecording = SpeakAction.builder()
                .withText("You have not recorded an audio file yet")
                .withNextAction(menu)
                .withErrorAction(error)
                .build();

        menu.setNextActionF(a -> {
            switch (a.getReceivedDigits()) {
                case "1":
                    return recordPrompt;
                case "2":
                    final var key = a.getTransactionAttribute(RecordAudioAction.RECORD_AUDIO_KEY);
                    if (key != null) {
                        // Some Audio has been recorded
                        return playAudio;
                    } else {
                        // No Audio has been recorded
                        return noRecording;
                    }
                default:
                    return MAIN_MENU;
            }
        });

        return menu;
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

    private final static HangupAction ACTION_HANGUP_LEG_B = HangupAction.builder().withParticipantTag(ParticipantTag.LEG_B).build();

    @Override
    protected Action callUpdateRequest(Action action, Map<String, String> args) {
        log.debug("Call Update Request received");

        if (action instanceof CallAndBridgeAction) {
            final var attrs = action.getTransactionAttributes();

            if (args != null) {
                final var phoneNumber = args.get("phoneNumber");
                if (phoneNumber != null) {
                    log.info("Update Requested with a transfer to number of " + phoneNumber);
                    attrs.put("transferNumber", phoneNumber);

                    log.info("Returning action to Disconnect LEG-B of the call");
                    ACTION_HANGUP_LEG_B.setTransactionAttributes(attrs);  // This is a special case where we need to pass thru attributes
                    return ACTION_HANGUP_LEG_B;
                }
            }

        }
        return null;
    }

}
