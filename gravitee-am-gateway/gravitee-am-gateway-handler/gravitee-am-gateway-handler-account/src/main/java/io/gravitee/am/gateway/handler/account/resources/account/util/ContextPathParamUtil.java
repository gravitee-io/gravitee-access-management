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
package io.gravitee.am.gateway.handler.account.resources.account.util;

import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Objects;

public class ContextPathParamUtil {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE = 0;

    public static final String PARAM_PAGE_SIZE = "size";
    public static final String PARAM_PAGE_NUMBER = "page";

    public static Integer getPageNumber(RoutingContext routingContext){
        return getPathParamAsInt(routingContext, PARAM_PAGE_NUMBER, DEFAULT_PAGE);
    }

    public static Integer getPageSize(RoutingContext routingContext){
        return getPathParamAsInt(routingContext, PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE);
    }

    public static Integer getPathParamAsInt(RoutingContext routingContext, String param, Integer defaultValue){
        String paramValue = routingContext.request().getParam(param);
        if (Objects.nonNull(paramValue)) {
            try {
                return Integer.parseInt(paramValue);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }else {
            return defaultValue;
        }
    }
}
