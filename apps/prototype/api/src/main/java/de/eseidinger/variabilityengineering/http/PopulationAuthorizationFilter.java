package de.eseidinger.variabilityengineering.http;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.Arrays;
import java.util.Set;

/**
 * Applies the fine-grained population policy to the verified group context
 * supplied by the developer-platform access boundary.
 */
public class PopulationAuthorizationFilter {

    static final String GROUP_IDS_HEADER = "X-Platform-Principal-Group-Ids";
    static final String USERS_GROUP = "grp_d8113bc5c0ca4b6092ae13386310268f";
    static final String GUESTS_GROUP = "grp_0c98f1205bfe456a8517417a38de98e0";

    static void requireAuthorized(HttpHeaders headers, String method) {
        var groups = groupIds(headers.getHeaderString(GROUP_IDS_HEADER));
        if (groups.contains(USERS_GROUP)) {
            return;
        }
        if (groups.contains(GUESTS_GROUP) && "GET".equals(method)) {
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
