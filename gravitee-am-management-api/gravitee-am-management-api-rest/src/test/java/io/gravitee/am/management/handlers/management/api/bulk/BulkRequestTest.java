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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
class BulkRequestTest {

    private static final int MAX_DELAY_SECONDS = 100;

    @Test
    void deserializesViaGenericRequest() throws Exception {
        var om = new ObjectMapper();
        var createCommmandJson = """
                            {
                              "action": "CREATE",
                              "items": [
                                {
                                  "email": "test@localhost.com"
                                },
                                {
                                    "email": "test2@local.host"
                                }
                              ]
                            }
                """;

        record EmailRecord(String email) {
        }

        var genericRequest = om.readValue(createCommmandJson, BulkRequest.Generic.class);
        var expectedResults = List.of("To: test@localhost.com", "To: test2@local.host");
        genericRequest.processOneByOne(EmailRecord.class, om, email -> Single.just(BulkOperationResult.ok("To: " + email.email())))
                .test()
                .assertComplete()
                .assertValueCount(1)
                .assertValue(res -> {
                    if (res.getEntity() instanceof BulkResponse<?> response) {
                        return response.getResults().size() == expectedResults.size()
                                && response.getResults().stream().map(BulkOperationResult::getBody).toList().containsAll(expectedResults);

                    } else {
                        return false;
                    }
                });
    }


    @Test
    void shouldReturnItemsInOriginalOrder() {
        var items = (IntStream.iterate(0, i -> i + 1).limit(20).boxed().toList());

        var expectedResults = items.stream().map(x -> BulkOperationResult.ok(pretendToDoStuff(x)).withIndex(x)).toList();

        // take control of the time since we don't want actual delays during test execution
        var testScheduler = new TestScheduler();

        var testObserver = new BulkRequest<>(BulkRequest.Action.CREATE, items)
                // randomize delays so that the results are emitted in random order
                .processOneByOne(i -> Single.just(BulkOperationResult.ok(pretendToDoStuff(i)))
                        .delay(randomDelay(), TimeUnit.SECONDS, testScheduler)
                        .doOnSuccess(res -> log.info("test time = {}s: got {}", String.format("%3d", testScheduler.now(TimeUnit.SECONDS)), res)))
                .map(Response::getEntity)
                .map(entity -> {
                    assertThat(entity).isInstanceOf(BulkResponse.class);
                    return (BulkResponse<String>) entity;
                })
                .test();

        testScheduler.advanceTimeBy(MAX_DELAY_SECONDS, TimeUnit.SECONDS);
        var actualResults = testObserver
                .assertComplete()
                .assertValueCount(1)
                .values()
                .get(0)
                .getResults();

        // even with out-of-order processing, results should be returned in the input order
        AssertionsForInterfaceTypes.assertThat(actualResults).containsExactlyElementsOf(expectedResults);


    }

    private static long randomDelay() {
        return (long) Math.floor(Math.random() * MAX_DELAY_SECONDS);
    }

    String pretendToDoStuff(int value) {
        return "(result for item #" + value + ")";
    }

}
