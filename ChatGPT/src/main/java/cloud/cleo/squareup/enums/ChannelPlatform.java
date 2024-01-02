package cloud.cleo.squareup.enums;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The Lex Channel Platform. Where the lex Input came from. If you are using the console or CLI to post text to Lex then
 * this is not set.
 *
 * https://docs.aws.amazon.com/lexv2/latest/dg/deploying-messaging-platform.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-chime.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-connect.html
 * https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-genesys.html
 * https://docs.aws.amazon.com/sms-voice/latest/userguide/phone-numbers-two-way-sms.html
 *
 * @author sjensen
 */
@AllArgsConstructor
public enum ChannelPlatform {
    CHIME("Amazon Chime SDK PSTN Audio"),
    TWILIO("Twilio"),
    FACEBOOK("Facebook"),
    SLACK("Slack"),
    CONNECT("Connect"),
    CONNECT_CHAT("Connect Chat"),
    GENESYS_CLOUD("Genesys Cloud"),
    PINPOINT("Pinpoint SMS"),
    UNKNOWN("No Platform Provided");

    private static final Map<String, ChannelPlatform> map = Stream.of(values())
            .collect(Collectors.toMap(ChannelPlatform::getChannel, e -> e));

    @Getter
    private final String channel;

    public static ChannelPlatform fromString(String channel) {
        // Check if the input is null or the map doesn't contain the channelString
        if (channel == null || !map.containsKey(channel)) {
            return UNKNOWN;
        }
        return map.get(channel);
    }

}
