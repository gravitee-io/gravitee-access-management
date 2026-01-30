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
package io.gravitee.am.gateway.handler.scim.service.impl;

import io.gravitee.am.gateway.handler.scim.model.BulkOperation;
import io.gravitee.am.gateway.handler.scim.model.BulkRequest;
import io.gravitee.am.gateway.handler.scim.model.BulkResponse;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.DummyAuthenticationContext;
import io.gravitee.am.identityprovider.api.DummyRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.gateway.handler.scim.spring.SCIMConfiguration;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.scim.model.BulkRequest.BULK_REQUEST_SCHEMA;
import static io.gravitee.common.http.HttpMethod.POST;
import static java.util.List.of;

public class BulkServiceImplTest {

    BulkServiceImpl service = new BulkServiceImpl(Mockito.mock(), Mockito.mock(), 1);

    @Test
    public void should_omit_further_operations_if_fail_on_error_counter_is_reached(){
        var failingOperation = new BulkOperation();
        failingOperation.setMethod(POST);
        List<BulkOperation> operations = of(failingOperation, failingOperation, failingOperation, failingOperation);
        test(operations, 2, 2);
    }

    @Test
    public void should_process_all_elements_when_failOnError_is_not_set(){
        var failingOperation = new BulkOperation();
        failingOperation.setMethod(POST);
        List<BulkOperation> operations = of(failingOperation, failingOperation, failingOperation, failingOperation);
        test(operations, null, operations.size());
    }

    @Test
    public void should_process_all_elements_when_failOnError_is_0(){
        var failingOperation = new BulkOperation();
        failingOperation.setMethod(POST);
        List<BulkOperation> operations = of(failingOperation, failingOperation, failingOperation, failingOperation);
        test(operations, 0, operations.size());
    }

    private void test(List<BulkOperation> operations, Integer failOnError, int expected) {
        var request = new BulkRequest();
        request.setOperations(operations);
        request.setFailOnErrors(failOnError);
        request.setSchemas(of(BULK_REQUEST_SCHEMA));

        Single<BulkResponse> response = service.processBulkRequest(request,
                new DummyAuthenticationContext(Map.of(), new DummyRequest()),
                "url",
                new Client(),
                new DefaultUser());
        response.test().assertValue(r -> r.getOperations().size() == expected);
    }

}
