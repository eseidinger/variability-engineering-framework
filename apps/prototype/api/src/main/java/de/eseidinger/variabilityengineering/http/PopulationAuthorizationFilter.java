package de.eseidinger.variabilityengineering.http;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * Applies the fine-grained population policy to the verified group context
 * supplied by the developer-platform access boundary.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PopulationAuthorizationFilter implements ContainerRequestFilter {

    static final String GROUP_IDS_HEADER = "X-Platform-Principal-Group-Ids";
    static final String USERS_GROUP = "grp_d8113bc5c0ca4b6092ae13386310268f";
    static final String GUESTS_GROUP = "grp_0c98f1205bfe456a8517417a38de98e0";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!requestContext.getUriInfo().getPath().startsWith("api/populations")) {
            return;
        }

        var groups = groupIds(requestContext.getHeaderString(GROUP_IDS_HEADER));
        if (groups.contains(USERS_GROUP)) {
            return;
        }
        if (groups.contains(GUESTS_GROUP) && "GET".equals(requestContext.getMethod())) {
            return;
        }
        throw new ForbiddenException("The current group is not permitted to access this endpoint");
    }

    private static Set<String> groupIds(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.stream(headerValue.trim().split("\\s+"))
                .filter(groupId -> !groupId.isBlank())
                .toList());
    }
}
