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


import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountAccessTokenRepositoryTest extends AbstractManagementTest {

    @Autowired
    private AccountAccessTokenRepository repository;

    @Test
    public void testCreateAndFindById() {
        var accountToken = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(UUID.randomUUID().toString())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        var accountAccessTokenCreated = repository.create(accountToken).blockingGet();

        var testObserver = repository.findById(accountAccessTokenCreated.tokenId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(result -> result.name().equals(accountToken.name()));
        testObserver.assertValue(ap -> ap.userId().equals(accountToken.userId()));
        testObserver.assertValue(ap -> ap.issuerId().equals(accountToken.issuerId()));
        testObserver.assertValue(ap -> ap.referenceId().equals(accountToken.referenceId()));
        testObserver.assertValue(ap -> ap.referenceType().equals(accountToken.referenceType()));
        testObserver.assertValue(ap -> ap.token().equals(accountToken.token()));
        testObserver.assertValue(ap -> ap.tokenId().equals(accountToken.tokenId()));
    }

    @Test
    public void testUpdate() {
        var accountToken = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(UUID.randomUUID().toString())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        repository.create(accountToken).blockingGet();

        var accountTokenToUpdate = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(accountToken.tokenId())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(UUID.randomUUID().toString())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        var updateResult = repository.update(accountTokenToUpdate).test();
        updateResult.awaitDone(10, TimeUnit.SECONDS);
        updateResult.assertComplete();
        updateResult.assertNoErrors();

        var testObserver = repository.findById(accountToken.tokenId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(result -> result.name().equals(accountTokenToUpdate.name()));
        testObserver.assertValue(ap -> ap.userId().equals(accountTokenToUpdate.userId()));
        testObserver.assertValue(ap -> ap.issuerId().equals(accountTokenToUpdate.issuerId()));
        testObserver.assertValue(ap -> ap.referenceId().equals(accountTokenToUpdate.referenceId()));
        testObserver.assertValue(ap -> ap.referenceType().equals(accountTokenToUpdate.referenceType()));
        testObserver.assertValue(ap -> ap.token().equals(accountTokenToUpdate.token()));
        testObserver.assertValue(ap -> ap.tokenId().equals(accountTokenToUpdate.tokenId()));
    }

    @Test
    public void testDelete() {
        var accountToken = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(UUID.randomUUID().toString())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        var accountAccessTokenCreated = repository.create(accountToken).blockingGet();

        var testObserver = repository.findById(accountAccessTokenCreated.tokenId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);

        var delete = repository.delete(accountAccessTokenCreated.tokenId()).test();
        delete.awaitDone(10, TimeUnit.SECONDS);
        delete.assertNoErrors();
        delete.assertComplete();

        testObserver = repository.findById(accountAccessTokenCreated.tokenId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void testFindByUserId() {
        var accountToken1 = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(UUID.randomUUID().toString())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        var accountToken2 = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(accountToken1.userId())
                .referenceId(accountToken1.referenceId())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        var accountToken3 = AccountAccessToken.builder()
                .token(UUID.randomUUID().toString())
                .tokenId(UUID.randomUUID().toString())
                .createdAt(new Date())
                .updatedAt(new Date())
                .name(UUID.randomUUID().toString())
                .issuerId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .referenceId(accountToken1.referenceId())
                .referenceType(ReferenceType.ORGANIZATION)
                .build();

        repository.create(accountToken1).blockingGet();
        repository.create(accountToken2).blockingGet();
        repository.create(accountToken3).blockingGet();

        var testObserver = repository.findByUserId(ReferenceType.ORGANIZATION, accountToken1.referenceId(), accountToken1.userId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(2);
    }

}
