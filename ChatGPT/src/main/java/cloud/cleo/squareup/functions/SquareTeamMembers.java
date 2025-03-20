package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.types.SearchTeamMembersFilter;
import com.squareup.square.types.SearchTeamMembersQuery;
import com.squareup.square.types.SearchTeamMembersRequest;
import com.squareup.square.types.TeamMember;
import com.squareup.square.types.TeamMemberStatus;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;

/**
 *  Return Employees (team members) from Square API
 * 
 * @author sjensen
 * @param <Request>
 */
public class SquareTeamMembers<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return "team_members";
    }

    @Override
    public String getDescription() {
        return "Return the Emoloyee names and phone numbers for this store location, do not give the phone numbers to the callers or give out the whole list.";
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
                return getSquareClient().teamMembers()
                        .search(SearchTeamMembersRequest.builder().query(SearchTeamMembersQuery.builder()
                                // Only return active employees at the defined location
                                .filter(SearchTeamMembersFilter.builder().status(TeamMemberStatus.ACTIVE).locationIds(List.of(System.getenv("SQUARE_LOCATION_ID"))).build())
                                .build()).build())
                        .get().getTeamMembers().get().stream()
                        .map(tm -> new Response(tm))
                        .toList();
            } catch (Exception ex) {
                log.error("Unhandled Error",ex);
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            } 
        };
    }

    @Getter
    private static class Response {

        @JsonPropertyDescription("Employee First Name")
        String first_name;
        @JsonPropertyDescription("Employee Last Name")
        String last_name;
        @JsonPropertyDescription("Employee Phone number in E164 format, to be used for call transfers, do not reveal or privide this directly")
        String phone_number;
        @JsonPropertyDescription("Employee Email address, to be used to send messages")
        String email;

        private Response(TeamMember tm){
            this.first_name = tm.getGivenName().orElse(null);
            this.last_name = tm.getFamilyName().orElse(null);
            this.phone_number = tm.getPhoneNumber().orElse(null);
            this.email = tm.getEmailAddress().orElse(null);
        }
    }

    private static class Request {
    }
   
    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }
}
