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
package io.gravitee.am.management.handlers.management.api.bulk;

import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class BulkRequestTest {

    private static final int MAX_DELAY_MS = 250;

    @Test
    void shouldReturnItemsInOriginalOrder() {
        var items = (IntStream.iterate(0, i -> i + 1).limit(20).boxed().toList());

        var expectedResults = items.stream().map(x -> BulkOperationResult.ok(pretendToDoStuff(x)).withIndex(x)).toList();

        var actualResults = new BulkRequest<>(items)
                // randomize delays so that the results are emitted in different order
                .processOneByOne(i -> Single.just(BulkOperationResult.ok(pretendToDoStuff(i))).delay((long) Math.floor(Math.random() * MAX_DELAY_MS), TimeUnit.MILLISECONDS))
                .map(Response::getEntity)
                .map(entity -> {
                    assertThat(entity).isInstanceOf(BulkResponse.class);
                    return (BulkResponse) entity;
                })
                .test()
                .awaitDone(MAX_DELAY_MS * 2, TimeUnit.MILLISECONDS)
                .assertComplete()
                .assertValueCount(1)
                .values()
                .get(0)
                .getResults();

        // even with out-of-order processing, results should be returned in the input order
        AssertionsForInterfaceTypes.assertThat(actualResults).containsExactlyElementsOf(expectedResults);


    }

    int pretendToDoStuff(int value) {
        return value + 100;
    }

}
