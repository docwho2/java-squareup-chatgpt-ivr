package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.SearchTeamMembersFilter;
import com.squareup.square.models.SearchTeamMembersQuery;
import com.squareup.square.models.SearchTeamMembersRequest;
import com.squareup.square.models.TeamMember;
import java.util.List;
import java.util.function.Function;
import lombok.Data;

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
                return getSquareClient().getTeamApi()
                        .searchTeamMembers(new SearchTeamMembersRequest.Builder().query(new SearchTeamMembersQuery.Builder()
                                // Only return active employees at the defined location
                                .filter(new SearchTeamMembersFilter.Builder().status("ACTIVE").locationIds(List.of(System.getenv("SQUARE_LOCATION_ID"))).build())
                                .build()).build())
                        .getTeamMembers().stream()
                        .map(tm -> new Response(tm))
                        .toList();
            } catch (Exception ex) {
                log.error("Unhandled Error",ex);
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            } 
        };
    }

    @Data
    private static class Response {

        @JsonPropertyDescription("Employee First Name")
        String first_name;
        @JsonPropertyDescription("Employee Last Name")
        String last_name;
        @JsonPropertyDescription("Employee Phone number in E164 format, to be used for call transfers, do not reveal or privide this directly")
        String phone_number;
        @JsonPropertyDescription("Employee Email address, to be used to send messages, do not reveal or provide this directly")
        String email;

        public Response(TeamMember tm){
            this.first_name = tm.getGivenName();
            this.last_name = tm.getFamilyName();
            this.phone_number = tm.getPhoneNumber();
            this.email = tm.getEmailAddress();
        }
    }

    private static class Request {
    }
   
    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }
}
