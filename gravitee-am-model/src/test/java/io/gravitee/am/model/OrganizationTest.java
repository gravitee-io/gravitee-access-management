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

package io.gravitee.am.model;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OrganizationTest {

    @Test
    public void should_clone_all_attribute() {
        final Organization organization = new Organization();
        organization.setDescription(UUID.randomUUID().toString());
        organization.setDescription(UUID.randomUUID().toString());
        organization.setDomainRestrictions(List.of(UUID.randomUUID().toString()));
        organization.setId(UUID.randomUUID().toString());
        organization.setHrids(List.of(UUID.randomUUID().toString()));
        organization.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        organization.setUpdatedAt(new Date());

        final Organization clone = new Organization(organization);
        Assertions.assertThat(organization).usingRecursiveComparison().isEqualTo(clone);
    }

}
