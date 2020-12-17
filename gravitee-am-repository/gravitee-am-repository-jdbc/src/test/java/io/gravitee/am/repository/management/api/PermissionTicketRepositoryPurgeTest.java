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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.jdbc.management.api.JdbcPermissionTicketRepository;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.NoSuchElementException;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionTicketRepositoryPurgeTest extends AbstractManagementTest {

    @Autowired
    private JdbcPermissionTicketRepository repository;

    private static final PermissionRequest permission = new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b"));

    @Test
    public void testPurge() throws TechnicalException {
        // create permission_ticket
        PermissionTicket permissionTicketNoExpireAt = new PermissionTicket().setPermissionRequest(Arrays.asList(permission));
        PermissionTicket ptValid = repository.create(permissionTicketNoExpireAt).blockingGet();
        PermissionTicket permissionTicket = new PermissionTicket().setPermissionRequest(Arrays.asList(permission));
        Instant now = Instant.now();
        permissionTicket.setExpireAt(new Date(now.plus(10, ChronoUnit.MINUTES).toEpochMilli()));
        PermissionTicket ptValid2 = repository.create(permissionTicket).blockingGet();
        PermissionTicket permissionTicketExpired = new PermissionTicket().setPermissionRequest(Arrays.asList(permission));
        permissionTicketExpired.setExpireAt(new Date(now.minus(10, ChronoUnit.MINUTES).toEpochMilli()));
        TestObserver<PermissionTicket> test = repository.create(permissionTicketExpired).test();
        test.awaitTerminalEvent();
        test.assertError(NoSuchElementException.class); // because expired, findById exclude it

        // fetch permission_ticket
        TestObserver<PermissionTicket> testObserver = repository.findById(ptValid.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertValue(this::isValid);
        testObserver = repository.findById(ptValid2.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertValue(this::isValid);

        TestObserver<Void> testPurge = repository.purgeExpiredData().test();
        testPurge.awaitTerminalEvent();
        testPurge.assertNoErrors();

        testObserver = repository.findById(ptValid.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertValue(this::isValid);
        testObserver = repository.findById(ptValid2.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertValue(this::isValid);
    }

    private boolean isValid(PermissionTicket pt) {
        return  pt.getPermissionRequest().size() == 1 &&
                pt.getPermissionRequest().get(0).getResourceId().equals("one") &&
                pt.getPermissionRequest().get(0).getResourceScopes().containsAll(Arrays.asList("a","b"));
    }

}
