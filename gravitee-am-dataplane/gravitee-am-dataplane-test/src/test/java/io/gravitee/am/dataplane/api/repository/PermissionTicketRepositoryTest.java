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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionTicketRepositoryTest extends AbstractDataPlaneTest {

    @Autowired
    private PermissionTicketRepository repository;

    private static final PermissionRequest permission = new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b"));

    @Test
    public void testFindById() {
        // create permission_ticket
        PermissionTicket permissionTicket = new PermissionTicket().setPermissionRequest(Arrays.asList(permission));
        permissionTicket.setClientId(UUID.randomUUID().toString());
        permissionTicket.setDomain(UUID.randomUUID().toString());
        permissionTicket.setUserId(UUID.randomUUID().toString());
        permissionTicket.setCreatedAt(new Date());
        permissionTicket.setExpireAt(new Date(Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        PermissionTicket ptCreated = repository.create(permissionTicket).blockingGet();

        // fetch permission_ticket
        TestObserver<PermissionTicket> testObserver = repository.findById(ptCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(this::isValid);
    }

    @Test
    public void update() {
        // create resource_set, resource_scopes being the most important field.
        PermissionRequest source = new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("c","d"));
        PermissionTicket permissionTicket = new PermissionTicket().setPermissionRequest(Arrays.asList(source));
        PermissionTicket ptCreated = repository.create(permissionTicket).blockingGet();
        PermissionTicket toUpdate = new PermissionTicket().setId(ptCreated.getId()).setPermissionRequest(Arrays.asList(permission));

        // fetch permission_ticket
        TestObserver<PermissionTicket> testObserver = repository.update(toUpdate).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(this::isValid);
    }

    @Test
    public void delete() {
        // create permission_ticket
        PermissionTicket permissionTicket = new PermissionTicket().setPermissionRequest(Arrays.asList(permission));
        PermissionTicket ptCreated = repository.create(permissionTicket).blockingGet();

        // fetch permission_ticket
        TestObserver<Void> testObserver = repository.delete(ptCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    private boolean isValid(PermissionTicket pt) {
        return  pt.getPermissionRequest().size() == 1 &&
                pt.getPermissionRequest().get(0).getResourceId().equals("one") &&
                pt.getPermissionRequest().get(0).getResourceScopes().containsAll(Arrays.asList("a","b"));
    }

}
