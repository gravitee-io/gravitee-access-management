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
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class EvaluableRedirectUri {
    private final String fullUri;

    public Maybe<String> evaluate(ExecutionContext ctx){
        return evaluateQuery(ctx)
                .map(evaluatedQuery -> UriBuilder.fromURIString(fullUri).query(evaluatedQuery).buildString())
                .switchIfEmpty(Maybe.just(removeELQueryParams()));
    }

    @SneakyThrows
    private Maybe<String> evaluateQuery(ExecutionContext ctx){
        String query = UriBuilder.fromURIString(fullUri)
                .build()
                .getQuery();
        if(query == null){
            return Maybe.empty();
        } else {
            return ctx
                    .getTemplateEngine()
                    .eval(query, String.class)
                    .doOnError(err -> log.error(err.getMessage()))
                    .onErrorResumeNext(err -> Maybe.error(new QueryParamParsingException("query parameter processing exception")));
        }
    }

    public String getRootUrl(){
        return UriBuilder.fromURIString(fullUri)
                .query(null)
                .fragment(null)
                .buildString();
    }

    public boolean matchRootUrl(String uri){
        String withoutQueryAndFragment = UriBuilder.fromURIString(uri)
                .query(null)
                .fragment(null)
                .buildString();
        return getRootUrl().equals(withoutQueryAndFragment);
    }

    @SneakyThrows
    public String removeELQueryParams(){
        String query = UriBuilder.fromURIString(fullUri)
                .build()
                .getQuery();
        if(query == null){
            return fullUri;
        }
        String[] pairs = query.split("&");
        String params = Stream.of(pairs)
                .filter(pair -> !pair.contains("{#"))
                .collect(Collectors.joining("&"));
        return UriBuilder.fromURIString(fullUri).query(params).buildString();
    }

}
