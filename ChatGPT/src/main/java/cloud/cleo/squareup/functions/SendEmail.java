package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.function.Function;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Send an Email message
 *
 * @author sjensen
 * @param <Request>
 */
public class SendEmail<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "send_email_message";
    }

    @Override
    public String getDescription() {
        return "Send Email message to an employee, use to relay information to an employee";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     *
     * @return
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {

            try {
                // Put the callingNumber in the subject if it exists, it might not if using lex console for example
                final var subject = getCallingNumber() == null ? r.subject :
                        "[From " + getCallingNumber() + "] " + r.subject;
                final var ses = SesClient.create();
                final var id = ses.sendEmail((email) -> {
                    email.destination(dest -> dest.toAddresses(r.employee_email))
                            .message((mesg) -> {
                                mesg.body((body) -> {
                                    body.text(cont -> cont.data(r.message));
                                }).subject(cont -> cont.data(subject));
                            }).source("chatgpt@copperfoxgifts.com");
                });

                log.info("Sent email to " + r.employee_email + " with id " + id);
                log.info("Subject: " + subject);
                log.info("Message: " + r.message);
                return mapper.createObjectNode().put("status", "SUCCESS").put("message", "The email has been successfuly sent.");
            } catch (Exception e) {
                log.error("Unhandled Error", e);
                return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, the email could not be sent.");
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("The employee email address")
        @JsonProperty(required = true)
        public String employee_email;

        @JsonPropertyDescription("Subject for the email message")
        @JsonProperty(required = true)
        public String subject;

        @JsonPropertyDescription("The message body to relay to the employee")
        @JsonProperty(required = true)
        public String message;
    }

    /**
     * Square must be enabled or their won't be a way to get email addresses
     *
     * @return
     */
    @Override
    protected boolean isEnabled() {
        return squareEnabled;
    }

}
