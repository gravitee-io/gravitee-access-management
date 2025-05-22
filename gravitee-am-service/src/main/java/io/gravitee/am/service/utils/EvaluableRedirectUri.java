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

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class EvaluableRedirectUri {
    private final static String URI_EL_PATTERN_BEGIN = "\\{\\$";
    private final static String ENGINE_EL_PATTERN_BEGIN = "{#";
    private final String fullUri;

    public Maybe<String> evaluate(ExecutionContext ctx){
        return evaluateQuery(ctx)
                .map(evaluatedQuery -> UriBuilder.fromURIString(fullUri).query(evaluatedQuery).buildString())
                .switchIfEmpty(Maybe.error(new IllegalStateException("URI evaluation failed")));
    }

    @SneakyThrows
    private Maybe<String> evaluateQuery(ExecutionContext ctx){
        String query = UriBuilder.fromURIString(fullUri)
                .build()
                .getQuery();
        if(query == null){
            return Maybe.empty();
        } else {
            String engineQuery = query.replaceAll(URI_EL_PATTERN_BEGIN, ENGINE_EL_PATTERN_BEGIN);
            return ctx
                    .getTemplateEngine()
                    .eval(engineQuery, String.class);
        }
    }

    @SneakyThrows
    public boolean containsEL(){
        String query = UriBuilder.fromURIString(fullUri)
                .build()
                .getQuery();
        return query.matches(".*" + URI_EL_PATTERN_BEGIN + ".*}.*");
    }

    public String getRootUrl(){
        return UriBuilder.fromURIString(fullUri)
                .buildRootUrl();
    }

    public boolean matchRootUrl(String uri){
        return getRootUrl().equals(UriBuilder.fromURIString(uri).buildRootUrl());
    }

    @SneakyThrows
    public String removeELQueryParams(){
        String query = UriBuilder.fromURIString(fullUri)
                .build()
                .getQuery();
        String[] pairs = query.split("&");
        String params = Stream.of(pairs)
                .filter(pair -> {
                    String[] split = pair.split("=");
                    if(split.length == 2){
                        return !split[1].matches(URI_EL_PATTERN_BEGIN + ".*}");
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.joining("&"));
        return UriBuilder.fromURIString(fullUri).query(params).buildString();
    }

}
