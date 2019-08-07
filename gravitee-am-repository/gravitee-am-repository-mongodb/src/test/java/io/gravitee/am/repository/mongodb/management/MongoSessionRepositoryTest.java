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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Session;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.SessionRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.UnsupportedEncodingException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoSessionRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Override
    public String collectionName() {
        return "sessions";
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create session
        Session session = new Session();
        session.setId("session-id");
        sessionRepository.create(session).blockingGet();

        // fetch domain
        TestObserver<Session> testObserver = sessionRepository.findById(session.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getId().equals("session-id"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        sessionRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Session session = new Session();
        session.setId("session-id");

        TestObserver<Session> testObserver = sessionRepository.create(session).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(sessionCreated -> sessionCreated.getId().equals(session.getId()));
    }

    @Test
    public void testUpdate() throws TechnicalException, UnsupportedEncodingException {
        // create session
        Session session = new Session();
        session.setId("session-id");
        Session sessionCreated = sessionRepository.create(session).blockingGet();

        // update session
        Session updatedSession = new Session();
        updatedSession.setId(sessionCreated.getId());
        updatedSession.setValue(new String("testUpdatedValue").getBytes("UTF-8"));

        TestObserver<Session> testObserver = sessionRepository.update(updatedSession).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> new String(s.getValue(), "UTF-8").equals("testUpdatedValue"));

    }

    @Test
    public void testDelete() throws TechnicalException {
        // create session
        Session session = new Session();
        session.setId("session-id");
        Session sessionCreated = sessionRepository.create(session).blockingGet();

        // fetch session
        TestObserver<Session> testObserver = sessionRepository.findById(sessionCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getId().equals(sessionCreated.getId()));

        // delete session
        TestObserver testObserver1 = sessionRepository.delete(sessionCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch session
        sessionRepository.findById(sessionCreated.getId()).test().assertEmpty();
    }

    @Test
    public void testClear() throws TechnicalException {
        // create session 1
        Session session = new Session();
        session.setId("session-id");
        Session sessionCreated = sessionRepository.create(session).blockingGet();

        // create session 2
        Session session2 = new Session();
        session2.setId("session-id-2");
        Session sessionCreated2 = sessionRepository.create(session2).blockingGet();

        // fetch session 1
        TestObserver<Session> testObserver = sessionRepository.findById(sessionCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getId().equals(sessionCreated.getId()));

        // fetch session 2
        TestObserver<Session> testObserver2 = sessionRepository.findById(sessionCreated2.getId()).test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(s -> s.getId().equals(sessionCreated2.getId()));

        // delete session
        TestObserver testObserver1 = sessionRepository.clear().test();
        testObserver1.awaitTerminalEvent();

        // fetch sessions
        sessionRepository.findById(sessionCreated.getId()).test().assertEmpty();
        sessionRepository.findById(sessionCreated2.getId()).test().assertEmpty();
    }
}
