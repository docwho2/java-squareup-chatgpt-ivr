/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The Lex Channel Platform.  Where the lex Input came from.  If you are using
 * the console or CLI to post text to Lex then this is not set.
 * 
 * https://docs.aws.amazon.com/lexv2/latest/dg/deploying-messaging-platform.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-chime.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-connect.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-genesys.html
 * @author sjensen
 */
@AllArgsConstructor
public enum ChannelPlatform {
    CHIME("Chime"),
    TWILIO("Twilio"),
    FACEBOOK("Facebook"),
    SLACK("Slack"),
    CONNECT("Connect"),
    CONNECT_CHAT("Connect Chat"),
    GENESYS_CLOUD("Genesys Cloud");

    @Getter
    private final String channel;

    public static ChannelPlatform fromString(String channel) {
        return switch (channel) {
            case "Chime" ->
                CHIME;
            case "Twilio" ->
                TWILIO;
            case "Facebook" ->
                FACEBOOK;
            case "Slack" ->
                SLACK;
            case "Connect" ->
                CONNECT;
            case "Connect Chat" ->
                CONNECT_CHAT;
            case "Genesys Cloud" ->
                GENESYS_CLOUD;
            default ->
                throw new RuntimeException("Unknown Channel Plaform [" + channel + "]");
        };
    }

}
