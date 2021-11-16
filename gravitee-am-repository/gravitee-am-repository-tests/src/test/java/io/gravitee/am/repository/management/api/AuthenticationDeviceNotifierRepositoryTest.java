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

import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.common.utils.UUID;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierRepositoryTest extends AbstractManagementTest {

    @Autowired
    private AuthenticationDeviceNotifierRepository repository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        AuthenticationDeviceNotifier plugin = buildNotifierPlugin();
        plugin.setReferenceId("testDomain");
        plugin.setReferenceType(ReferenceType.DOMAIN);
        repository.create(plugin).blockingGet();

        TestSubscriber<AuthenticationDeviceNotifier> testSubscriber = repository.findByReference(ReferenceType.DOMAIN,"testDomain").test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    private AuthenticationDeviceNotifier buildNotifierPlugin() {
        AuthenticationDeviceNotifier plugin = new AuthenticationDeviceNotifier();
        String random = UUID.random().toString();
        plugin.setName("name"+random);
        plugin.setReferenceId("domain"+random);
        plugin.setReferenceType(ReferenceType.DOMAIN);
        plugin.setConfiguration("{\"config\": \"" + random +"\"}");
        plugin.setType("type"+random);
        plugin.setCreatedAt(new Date());
        plugin.setUpdatedAt(new Date());
        return plugin;
    }

    @Test
    public void testFindById() throws TechnicalException {
        AuthenticationDeviceNotifier plugin = buildNotifierPlugin();
        AuthenticationDeviceNotifier pluginCreated = repository.create(plugin).blockingGet();

        TestObserver<AuthenticationDeviceNotifier> testObserver = repository.findById(pluginCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(pluginCreated.getId()));
        testObserver.assertValue(bd -> bd.getName().equals(pluginCreated.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(pluginCreated.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(pluginCreated.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(pluginCreated.getType()));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        repository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        AuthenticationDeviceNotifier plugin = buildNotifierPlugin();

        TestObserver<AuthenticationDeviceNotifier> testObserver = repository.create(plugin).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId() != null);
        testObserver.assertValue(bd -> bd.getName().equals(plugin.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(plugin.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(plugin.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(plugin.getType()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        AuthenticationDeviceNotifier plugin = buildNotifierPlugin();
        AuthenticationDeviceNotifier pluginCreated = repository.create(plugin).blockingGet();

        AuthenticationDeviceNotifier pluginToUpdate = buildNotifierPlugin();
        pluginToUpdate.setId(pluginCreated.getId());
        pluginToUpdate.setName("testUpdatedName");

        TestObserver<AuthenticationDeviceNotifier> testObserver = repository.update(pluginToUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(pluginCreated.getId()));
        testObserver.assertValue(bd -> bd.getName().equals(pluginToUpdate.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(pluginToUpdate.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(pluginToUpdate.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(pluginToUpdate.getType()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        AuthenticationDeviceNotifier plugin = buildNotifierPlugin();
        AuthenticationDeviceNotifier pluginCreated = repository.create(plugin).blockingGet();

        TestObserver<AuthenticationDeviceNotifier> testObserver = repository.findById(pluginCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getName().equals(pluginCreated.getName()));

        TestObserver testObserver1 = repository.delete(pluginCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        repository.findById(pluginCreated.getId()).test().assertEmpty();
    }

}
