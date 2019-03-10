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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoRefreshTokenRepositoryTest extends AbstractOAuth2RepositoryTest {

    @Autowired
    private MongoRefreshTokenRepository refreshTokenRepository;

    @Override
    public String collectionName() {
        return "refresh_tokens";
    }

    @Test
    public void shouldNotFindToken() {
        TestObserver<RefreshToken> observer = refreshTokenRepository.findByToken("unknown-token").test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindToken() {
        RefreshToken token = new RefreshToken();
        token.setId(RandomString.generate());
        token.setToken("my-token");

        TestObserver<RefreshToken> observer = refreshTokenRepository
                .create(token)
                .toCompletable()
                .andThen(refreshTokenRepository.findByToken("my-token"))
                .test();

        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(1);
        observer.assertNoErrors();
    }

    @Test
    public void shouldDelete() {
        RefreshToken token = new RefreshToken();
        token.setId(RandomString.generate());
        token.setId("my-token");
        token.setToken("my-token");

        refreshTokenRepository
                .create(token)
                .toCompletable()
                .andThen(refreshTokenRepository.delete("my-token"))
                .andThen(refreshTokenRepository.findByToken("my-token"))
                .test().assertEmpty();
    }
}
