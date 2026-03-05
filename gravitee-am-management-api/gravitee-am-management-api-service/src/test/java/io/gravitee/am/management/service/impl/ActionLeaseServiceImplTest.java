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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.management.api.ActionLeaseRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionLeaseServiceImplTest {

    private static final String NODE_ID = "node-1";
    private static final String ACTION = "auditSweeper";
    private static final Duration DURATION = Duration.ofSeconds(3600);

    @Mock
    private ActionLeaseRepository actionLeaseRepository;

    @Mock
    private Node node;

    @InjectMocks
    private ActionLeaseServiceImpl actionLeaseService;

    @Test
    void should_acquire_lease_using_current_node_id() {
        // Given
        ActionLease lease = new ActionLease();
        when(node.id()).thenReturn(NODE_ID);
        when(actionLeaseRepository.acquireLease(ACTION, NODE_ID, DURATION)).thenReturn(Maybe.just(lease));

        // When
        TestObserver<ActionLease> observer = actionLeaseService.acquireLease(ACTION, DURATION).test();

        // Then
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(lease);
        verify(actionLeaseRepository).acquireLease(ACTION, NODE_ID, DURATION);
    }

    @Test
    void should_return_empty_when_lease_not_acquired() {
        // Given
        when(node.id()).thenReturn(NODE_ID);
        when(actionLeaseRepository.acquireLease(ACTION, NODE_ID, DURATION)).thenReturn(Maybe.empty());

        // When
        TestObserver<ActionLease> observer = actionLeaseService.acquireLease(ACTION, DURATION).test();

        // Then
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
    }

    @Test
    void should_propagate_repository_error() {
        // Given
        RuntimeException repositoryError = new RuntimeException("DB unavailable");
        when(node.id()).thenReturn(NODE_ID);
        when(actionLeaseRepository.acquireLease(ACTION, NODE_ID, DURATION)).thenReturn(Maybe.error(repositoryError));

        // When
        TestObserver<ActionLease> observer = actionLeaseService.acquireLease(ACTION, DURATION).test();

        // Then
        observer.assertError(repositoryError);
        observer.assertNotComplete();
    }
}
