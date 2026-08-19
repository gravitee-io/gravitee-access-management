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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.TokenMongo;
import io.gravitee.am.repository.oauth2.api.TokenRepository.TokenType;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the single round-trip: reverting to two insertOne calls would keep the behaviour tests green
 * but lose what the batch buys on MongoDB, which does not roll back an ordered insertMany.
 *
 * @author GraviteeSource Team
 */
public class MongoTokenRepositoryBatchTest {

    private static final long TIMEOUT_SECONDS = 10;

    private MongoCollection<TokenMongo> tokenCollection;
    private MongoTokenRepository repository;

    @Before
    @SuppressWarnings("unchecked")
    public void before() {
        tokenCollection = mock(MongoCollection.class);
        repository = new MongoTokenRepository();
        ReflectionTestUtils.setField(repository, "tokenCollection", tokenCollection);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInsertBothTokensInSingleCall() {
        when(tokenCollection.insertMany(anyList())).thenReturn(Flowable.just(InsertManyResult.unacknowledged()));

        AccessToken accessToken = newAccessToken();
        RefreshToken refreshToken = newRefreshToken();

        TestObserver<Void> observer = repository.create(accessToken, refreshToken).test();
        observer.awaitDone(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        ArgumentCaptor<List<TokenMongo>> captor = ArgumentCaptor.forClass(List.class);
        verify(tokenCollection, times(1)).insertMany(captor.capture());
        verify(tokenCollection, never()).insertOne(any());

        List<TokenMongo> inserted = captor.getValue();
        assertEquals(2, inserted.size());
        assertEquals(TokenType.ACCESS_TOKEN, inserted.get(0).getType());
        assertEquals(accessToken.getToken(), inserted.get(0).getJti());
        assertEquals(TokenType.REFRESH_TOKEN, inserted.get(1).getType());
        assertEquals(refreshToken.getToken(), inserted.get(1).getJti());
    }

    @Test
    public void shouldNotInsertManyWhenRefreshTokenIsNull() {
        when(tokenCollection.insertOne(any())).thenReturn(Flowable.just(InsertOneResult.unacknowledged()));

        TestObserver<Void> observer = repository.create(newAccessToken(), null).test();
        observer.awaitDone(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(tokenCollection, times(1)).insertOne(any());
        verify(tokenCollection, never()).insertMany(anyList());
    }

    private AccessToken newAccessToken() {
        AccessToken accessToken = new AccessToken();
        accessToken.setId("at-id");
        accessToken.setToken("at-jti");
        return accessToken;
    }

    private RefreshToken newRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId("rt-id");
        refreshToken.setToken("rt-jti");
        return refreshToken;
    }
}
