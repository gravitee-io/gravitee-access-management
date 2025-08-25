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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowContextRepositoryTest extends AbstractGatewayTest {
    private String TRANSACTION_ID = null;

    @Autowired
    private AuthenticationFlowContextRepository authenticationFlowContextRepository;

    @Before
    public void init() {
        TRANSACTION_ID = "TRX_"+ UUID.randomUUID().toString();
    }

    @Test
    public void shouldNotFindSession() {
        TestSubscriber<AuthenticationFlowContext> observer = authenticationFlowContextRepository.findByTransactionId("unknown-sessions").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindLastSession() {
        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.findLastByTransactionId("unknown-sessions").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldCreate() {
        AuthenticationFlowContext entity = generateAuthContext();
        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.create(entity).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        assertSameContext(entity, observer);
    }

    @Test
    public void shouldReplaceExisting() {
        AuthenticationFlowContext entity = generateAuthContext();
        AuthenticationFlowContext entity2 = generateAuthContext();

        assertEquals(entity.identifier(), entity2.identifier());

        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.create(entity)
                .concatMap(ctx -> authenticationFlowContextRepository.replace(entity2))
                .concatMapMaybe(ctx -> authenticationFlowContextRepository.findById(entity.identifier()))
                .test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        assertSameContext(entity, observer);
    }

    @Test
    public void shouldSaveNewIfReplace() {
        AuthenticationFlowContext entity = generateAuthContext();
        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.replace(entity)
                .test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        assertSameContext(entity, observer);
    }

    private void assertSameContext(AuthenticationFlowContext entity, TestObserver<AuthenticationFlowContext> observer) {
        observer.assertValue(ctx -> ctx.getTransactionId() != null && ctx.getTransactionId().equals(entity.getTransactionId()));
        observer.assertValue(ctx -> ctx.getVersion() == entity.getVersion());
        observer.assertValue(ctx -> ctx.getData() != null && ctx.getData().size()  == entity.getData().size());
        observer.assertValue(ctx -> ctx.getData().get("Key") != null &&  ctx.getData().get("Key").equals(entity.getData().get("Key")));
    }


    @Test
    public void shouldDelete() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<Void> testObserver = authenticationFlowContextRepository.delete(TRANSACTION_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertNoValues();
    }

    @Test
    public void shouldDeleteSingle() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<Void> testObserver = authenticationFlowContextRepository.delete(TRANSACTION_ID, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertValueCount(1);

        AuthenticationFlowContext readValue = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).blockingFirst();
        assertNotNull(readValue);
        assertEquals("Expected version 2 because version 1 should be deleted", 2, readValue.getVersion());
    }

    @Test
    public void shouldFind() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<AuthenticationFlowContext> testObserver = authenticationFlowContextRepository.findLastByTransactionId(TRANSACTION_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        assertSameContext(entity, testObserver);
    }


    @Test
    public void shouldNotFind_NullTransactionId() {
        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findByTransactionId(null).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoValues();
        testList.assertNoErrors();
    }

    @Test
    public void shouldNotFindExpiredData() {
        AuthenticationFlowContext entity = generateAuthContext(Instant.now().minus(10, ChronoUnit.MINUTES), 1);
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findByTransactionId(TRANSACTION_ID).test();
        testList.awaitDone(10, TimeUnit.SECONDS);
        testList.assertNoErrors();
        testList.assertValueCount(1);

        TestObserver<AuthenticationFlowContext> testObserver = authenticationFlowContextRepository.findLastByTransactionId(TRANSACTION_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        assertSameContext(entity, testObserver);
    }

    protected AuthenticationFlowContext generateAuthContext() {
        return generateAuthContext(Instant.now(), 1);
    }

    protected AuthenticationFlowContext generateAuthContext(Instant now, int version) {
        AuthenticationFlowContext entity = new AuthenticationFlowContext();
        entity.setVersion(version);
        entity.setTransactionId(TRANSACTION_ID);
        entity.setData(Collections.singletonMap("Key", "Value"));
        entity.setCreatedAt(new Date(now.toEpochMilli()));
        entity.setExpireAt(new Date(now.plus(2, ChronoUnit.MINUTES).toEpochMilli()));
        return entity;
    }
}
