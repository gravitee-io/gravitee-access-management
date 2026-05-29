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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Exercises the {@code OrganizationResource} entry point and its sub-resource locator
 * chain ({@code /organizations/{orgId}} -> domains).
 *
 * @author GraviteeSource Team
 */
class OrganizationResourceTest extends AutomationJerseySpringTest {

    @Test
    void domains_collection_is_reachable_through_organization_entry_point() {
        when(domainService.findAllByEnvironment(eq(ORG_ID), eq(ENV_ID)))
                .thenReturn(Flowable.empty());

        Response response = domainsTarget().request().get();

        assertEquals(200, response.getStatus());
    }

    @Test
    void unknown_organization_sub_path_is_404() {
        Response response = orgTarget().path("not-a-resource").request().get();

        assertEquals(404, response.getStatus());
    }
}
