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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.PasswordPolicyEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

public class PasswordPoliciesResourceTest extends JerseySpringTest {
    @Test
    public void shouldGetPasswordPolicies() {
        final String domainId = "domain1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);
        PasswordPolicy passwordPolicy1 = new PasswordPolicy();
        passwordPolicy1.setId("policyId1");
        passwordPolicy1.setName("policy1");
        passwordPolicy1.setDefaultPolicy(Boolean.TRUE);
        PasswordPolicy passwordPolicy2 = new PasswordPolicy();
        passwordPolicy2.setId("policyId2");
        passwordPolicy2.setName("policy2");
        passwordPolicy2.setDefaultPolicy(Boolean.FALSE);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.just(passwordPolicy1, passwordPolicy2)).when(passwordPolicyService).findByDomain(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("password-policies")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<PasswordPolicyEntity> policies = readListEntity(response, PasswordPolicyEntity.class);
        assertEquals(2, policies.size());
    }

    @Test
    public void shouldGetPasswordPoliciesReturnNoContent() {
        final String domainId = "domain1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.empty()).when(passwordPolicyService).findByDomain(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("password-policies")
                .request()
                .get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }
}
