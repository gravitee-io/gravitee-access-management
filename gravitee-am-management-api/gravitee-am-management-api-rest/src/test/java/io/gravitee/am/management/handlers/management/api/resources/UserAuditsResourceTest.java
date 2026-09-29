/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
@ContextConfiguration(classes = UserAuditsResourceTest.ManagementAuditConfiguration.class)
public class UserAuditsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String USER_ID = "user-id";
    private static final String OUTCOME_MESSAGE = "signed in";

    @Autowired
    private AuditService managementAuditService;

    @Test
    public void shouldIncludeOutcomeMessageWhenDomainAuditReadIsGranted() {
        grantDomainPermissions(DOMAIN_ID, Permission.DOMAIN_USER, Permission.DOMAIN_AUDIT);
        doReturn(Single.just(new Page<>(List.of(auditWithMessage()), 0, 1)))
                .when(managementAuditService).search(eq(DOMAIN_ID), any(), anyInt(), anyInt());

        final Response response = listUserAudits();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final Map<String, Object> outcome = firstOutcome(response);
        assertEquals("success", outcome.get("status"));
        assertEquals(OUTCOME_MESSAGE, outcome.get("message"));
    }

    @Test
    public void shouldOmitOutcomeMessageWithoutDomainAuditRead() {
        grantDomainPermissions(DOMAIN_ID, Permission.DOMAIN_USER);
        doReturn(Single.just(new Page<>(List.of(auditWithMessage()), 0, 1)))
                .when(managementAuditService).search(eq(DOMAIN_ID), any(), anyInt(), anyInt());

        final Response response = listUserAudits();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final Map<String, Object> outcome = firstOutcome(response);
        assertEquals("success", outcome.get("status"));
        assertFalse(outcome.containsKey("message"));
    }

    private Response listUserAudits() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("audits").request().get();
    }

    private void grantDomainPermissions(String domainId, Permission... permissions) {
        final Membership membership = new Membership();
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setReferenceId(domainId);
        final Map<Permission, Set<Acl>> held = new EnumMap<>(Permission.class);
        for (Permission permission : permissions) {
            held.put(permission, EnumSet.of(Acl.READ));
        }
        final Map<Membership, Map<Permission, Set<Acl>>> byMembership = Map.of(membership, held);
        doAnswer(invocation -> Single.just(invocation.<PermissionAcls>getArgument(1).match(byMembership)))
                .when(permissionService).hasPermission(any(), any(PermissionAcls.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstOutcome(Response response) {
        final Map<String, Object> body = readEntity(response, Map.class);
        final List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertEquals(1, data.size());
        final Map<String, Object> outcome = (Map<String, Object>) data.get(0).get("outcome");
        assertTrue(outcome.containsKey("status"));
        return outcome;
    }

    @Configuration
    static class ManagementAuditConfiguration {

        @Bean
        public AuditService managementAuditService() {
            return mock(AuditService.class);
        }
    }

    private static Audit auditWithMessage() {
        final AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        outcome.setMessage(OUTCOME_MESSAGE);
        final Audit audit = new Audit();
        audit.setId("audit-1");
        audit.setType("USER_LOGIN");
        audit.setOutcome(outcome);
        return audit;
    }
}
