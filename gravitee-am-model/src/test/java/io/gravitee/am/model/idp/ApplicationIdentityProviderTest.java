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
package io.gravitee.am.model.idp;

import org.junit.Test;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
 * A client holds its identity providers in a sorted set, and the login path takes the first one
 * whose selection rule matches. This ordering is therefore what decides which provider a user is
 * routed to when more than one rule matches them, which on a multi-tenant domain means it decides
 * which tenant they reach.
 *
 * @author GraviteeSource Team
 */
public class ApplicationIdentityProviderTest {

    @Test
    public void providersAreOrderedByAscendingPriorityWhicheverOrderTheyWereAddedIn() {
        List<String> addedLowestFirst = identitiesOf(
                provider("idp-first", 1),
                provider("idp-second", 5),
                provider("idp-third", 9));

        List<String> addedHighestFirst = identitiesOf(
                provider("idp-third", 9),
                provider("idp-second", 5),
                provider("idp-first", 1));

        assertEquals("lowest priority value must be routed to first",
                List.of("idp-first", "idp-second", "idp-third"), addedLowestFirst);

        // Asserted from both directions on purpose. The comparator is the only thing that should
        // decide the order here, so a result that tracked the order the providers were added in
        // would mean routing depended on whatever order the store happened to return them.
        assertEquals("ordering must come from priority, not from insertion order",
                addedLowestFirst, addedHighestFirst);
    }

    private static ApplicationIdentityProvider provider(String identity, int priority) {
        var appIdp = new ApplicationIdentityProvider();
        appIdp.setIdentity(identity);
        appIdp.setPriority(priority);
        return appIdp;
    }

    private static List<String> identitiesOf(ApplicationIdentityProvider... providers) {
        SortedSet<ApplicationIdentityProvider> sorted = new TreeSet<>();
        for (ApplicationIdentityProvider provider : providers) {
            sorted.add(provider);
        }
        return sorted.stream().map(ApplicationIdentityProvider::getIdentity).toList();
    }
}
