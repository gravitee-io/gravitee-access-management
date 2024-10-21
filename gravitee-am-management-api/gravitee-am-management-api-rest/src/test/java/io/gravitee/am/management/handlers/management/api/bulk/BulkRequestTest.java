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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

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
                                  "value": "some text"
                                },
                                {
                                    "value": "error 1234"
                                },
                                {}
                              ]
                            }
                """;

        record TestRecord(String value) {
        }

        var genericRequest = om.readValue(createCommmandJson, BulkRequest.Generic.class);
        var responseEntity = genericRequest.processOneByOne(TestRecord.class, om, item -> {
                    if (StringUtils.isBlank(item.value())) {
                        return Single.just(BulkOperationResult.error(Response.Status.CONFLICT));
                    }
                    else if (item.value().startsWith("error")) {
                        return Single.just(BulkOperationResult.error(Response.Status.BAD_REQUEST, new RuntimeException(item.value())));
                    } else {
                        return Single.just(BulkOperationResult.ok("Done: " + item.value()));
                    }
                })
                .test()
                .assertComplete()
                .assertValueCount(1)
                .values()
                .get(0);

        @SuppressWarnings("unchecked") var results = (BulkResponse<String>) responseEntity.getEntity();
        AssertionsForInterfaceTypes.assertThat(results.getResults()).anySatisfy(it -> {
                    assertThat(it.httpStatus()).isEqualTo(Response.Status.OK);
                    assertThat(it.getBody()).startsWith("Done: ");
                })
                .anySatisfy(it -> {
                    assertThat(it.httpStatus()).isEqualTo(Response.Status.BAD_REQUEST);
                    assertThat(it.getErrorDetails()).asInstanceOf(InstanceOfAssertFactories.map(String.class, String.class))
                            .containsKeys("message", "error");
                })
                .anySatisfy(it -> {
                    assertThat(it.httpStatus()).isEqualTo(Response.Status.CONFLICT);
                    assertThat(it.getErrorDetails()).isEqualTo("unknown error");
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
                    //noinspection unchecked
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
