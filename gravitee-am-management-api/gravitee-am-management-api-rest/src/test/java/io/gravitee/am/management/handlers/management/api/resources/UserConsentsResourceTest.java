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
import io.gravitee.am.management.handlers.management.api.model.ScopeApprovalEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetUserConsents() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final User mockUser = new User();
        mockUser.setId("user-id-1");


        final ScopeApprovalEntity scopeApproval = new ScopeApprovalEntity();
        scopeApproval.setUserId(UserId.internal("user-id-1"));
        scopeApproval.setClientId("clientId");
        scopeApproval.setScope("scope");
        scopeApproval.setDomain(domainId);


        doReturn(Single.just(List.of(scopeApproval))).when(scopeApprovalAdapter).getUserConsents(anyString(), anyString(), anyString());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .path(mockUser.getId())
                .path("consents")
                .queryParam("clientId", "clientId")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetUserConsents_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .path("user1")
                .path("consents")
                .request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldRevokeUserConsents() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final User mockUser = new User();
        mockUser.setId("user-id-1");

        doReturn(Completable.complete()).when(scopeApprovalAdapter).revokeUserConsents(any(), any(),any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("users")
                .path(mockUser.getId())
                .path("consents")
                .queryParam("clientId", "clientId")
                .request()
                .delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

}
