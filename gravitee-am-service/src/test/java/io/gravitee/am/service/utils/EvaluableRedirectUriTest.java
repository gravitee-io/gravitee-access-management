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
package io.gravitee.am.service.utils;

import io.gravitee.am.common.exception.oauth2.QueryParamParsingException;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.gateway.api.ExecutionContext;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

public class EvaluableRedirectUriTest {

    @Test
    public void should_evaluate_single_el_param(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?param={#context.attributes['test']}");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value"));


        redirectUri.evaluate(executionContext).test()
                .assertValue(value -> value.equals("https://example.com/?param=value"));
    }

    @Test
    public void should_evaluate_multiple_el_param(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?param={#context.attributes['test']}&param2={#context.attributes['test2']}");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value", "test2", "value2"));


        redirectUri.evaluate(executionContext).test()
                .assertValue(value -> value.equals("https://example.com/?param=value&param2=value2"));
    }

    @Test
    public void should_evaluate_multiple_el_param_2(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?a=1&param={#context.attributes['test']}&c=3&param2={#context.attributes['test2']}&b=2");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value", "test2", "value2"));


        redirectUri.evaluate(executionContext).test()
                .assertValue(value -> value.equals("https://example.com/?a=1&param=value&c=3&param2=value2&b=2"));
    }

    @Test
    public void should_throws_ex_on_invalid_el(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?param={#context.attributes['test']");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value"));


        redirectUri.evaluate(executionContext).test()
                .assertError(err -> err instanceof QueryParamParsingException);
    }

    @Test
    public void should_evaluate_to_empty_if_value_is_missing(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?param={#context.attributes['test']}");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of());


        redirectUri.evaluate(executionContext).test()
                .assertValue(value -> value.equals("https://example.com/?param="));
    }

    @Test
    public void should_evaluate_single_el_param_with_fragment(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?param={#context.attributes['test']}#fragment");
        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value"));


        redirectUri.evaluate(executionContext).test()
                .assertValue(value -> value.equals("https://example.com/?param=value#fragment"));
    }

    @Test
    public void should_return_base_url(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/test?param={#context.attributes['test']}#fragment");
        Assertions.assertEquals("https://example.com/test", redirectUri.getRootUrl());
    }

    @Test
    public void should_match_by_base_url(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/test?param={#context.attributes['test']}#fragment");
        Assertions.assertTrue(redirectUri.matchRootUrl("https://example.com/test?param2={#context.attributes['test2']}"));
    }

    @Test
    public void should_remove_el_params(){
        EvaluableRedirectUri redirectUri = new EvaluableRedirectUri("https://example.com/?a=3&param={#context.attributes['test']}#fragment");
        Assertions.assertEquals("https://example.com/?a=3#fragment", redirectUri.removeELQueryParams());
    }

}