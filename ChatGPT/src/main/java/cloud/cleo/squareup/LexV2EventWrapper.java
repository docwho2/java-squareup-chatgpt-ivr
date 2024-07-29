/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import cloud.cleo.squareup.ChatGPTLambdaPinpoint.PinpointEvent;
import cloud.cleo.squareup.LexV2Event.Bot;
import cloud.cleo.squareup.enums.*;
import static cloud.cleo.squareup.enums.ChannelPlatform.*;
import cloud.cleo.squareup.lang.LangUtil;
import cloud.cleo.squareup.lang.LangUtil.LanguageIds;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Wrapper for Lex Input Event to add utility functions.
 *
 * @author sjensen
 */
public class LexV2EventWrapper {

    /**
     * The underlying Lex V2 Event.
     */
    @Getter(AccessLevel.PUBLIC)
    private final LexV2Event event;

    /**
     * Language Support.
     */
    @Getter(AccessLevel.PUBLIC)
    private final LangUtil lang;

    /**
     * Wrap a Normal LexV2Event.
     * 
     * @param event 
     */
    public LexV2EventWrapper(LexV2Event event) {
        this.event = event;
        this.lang = new LangUtil(event.getBot().getLocaleId());
    }

    /**
     * Turn a Pinpoint Event into a Wrapped LexEvent.
     *
     * @param ppe
     */
    public LexV2EventWrapper(PinpointEvent ppe) {
        this(LexV2Event.builder()
                .withInputMode(LexInputMode.TEXT.getMode())
                // Exclude + from the E164 to be consistant with Twilio (shouldn't use + in sessionID)
                .withSessionId(ppe.getOriginationNumber().substring(1))
                // Mimic Platform input type of Pinpoint
                .withRequestAttributes(Map.of("x-amz-lex:channels:platform", ChannelPlatform.PINPOINT.getChannel()))
                // The incoming SMS body will be in the input Transcript
                .withInputTranscript(ppe.getMessageBody())
                // SMS has no locale target, just use en_US
                .withBot(Bot.builder().withLocaleId("en_US").build())
                // Need a Blank Session attributes
                .withSessionState(LexV2Event.SessionState.builder().withSessionAttributes(new HashMap<>()).build())
                .build());
    }

    /**
     * The Java Locale for this Bot request.
     *
     * @return
     */
    public Locale getLocale() {
        return lang.getLocale();
    }

    /**
     * Get a Language Specific String.
     *
     * @param id
     * @return
     */
    public String getLangString(LanguageIds id) {
        return lang.getString(id);
    }

    /**
     * Return Input Mode as Enumeration.
     *
     * @return InputMode enumeration
     */
    public LexInputMode getInputMode() {
        return LexInputMode.fromString(event.getInputMode());
    }

    /**
     * Is the event based on speech input (or DTMF which is still voice).
     *
     * @return
     */
    public boolean isVoice() {
        return switch (getInputMode()) {
            case SPEECH, DTMF ->
                true;
            default ->
                false;
        };
    }

    /**
     * Is the event based on text input.
     *
     * @return
     */
    public boolean isText() {
        return switch (getInputMode()) {
            case TEXT ->
                true;
            default ->
                false;
        };
    }

    /**
     * The Channel this event came from. Chime, Twilio, Facebook, etc..
     *
     * @return
     */
    public ChannelPlatform getChannelPlatform() {
        if (event.getRequestAttributes() != null && event.getRequestAttributes().containsKey("x-amz-lex:channels:platform")) {
            return ChannelPlatform.fromString(event.getRequestAttributes().get("x-amz-lex:channels:platform"));
        }
        // Unknown will be something new we haven't accounted for or direct Lex API calls (console, aws cli, etc..)
        return ChannelPlatform.UNKNOWN;
    }

    /**
     * Get the calling (or SMS originating) number for the session. For Channels like Facebook or CLI testing, this will
     * not be available and null.
     *
     * @return E164 number or null if not applicable to channel.
     */
    public String getPhoneE164() {
        return switch (getChannelPlatform()) {
            case CHIME ->
                // For Chime we will pass in the calling number as Session Attribute callingNumber
                event.getSessionState().getSessionAttributes() != null
                ? event.getSessionState().getSessionAttributes().get("callingNumber") : null;
            case TWILIO, PINPOINT ->
                // Twilio channel will use sessiond ID, however without +, so prepend to make it full E164
                "+".concat(event.getSessionId());
            default ->
                null;
        };
    }

    /**
     * The textual input to process.
     *
     * @return
     */
    public String getInputTranscript() {
        return event.getInputTranscript();
    }

    /**
     * The Intent for this request.
     *
     * @return
     */
    public String getIntent() {
        return event.getSessionState().getIntent().getName();
    }

    /**
     * Lex Session Attributes.
     *
     * @return
     */
    public Map<String, String> getSessionAttributes() {
        return event.getSessionState().getSessionAttributes();
    }

    /**
     * Session Id for the interaction.
     *
     * @return
     */
    public String getSessionId() {
        return event.getSessionId();
    }

    /**
     * Is this request from the Facebook Channel.
     *
     * @return
     */
    public boolean isFacebook() {
        return getChannelPlatform().equals(FACEBOOK);
    }

}
