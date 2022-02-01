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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp;

import io.gravitee.am.gateway.handler.common.vertx.web.handler.CSPHandler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CspHandlerImpl implements CSPHandler {

    private final io.vertx.reactivex.ext.web.handler.CSPHandler delegate;

    public CspHandlerImpl(Boolean isReportOnly, List<String> directives) {
        // adds "default-src": "self" as default configuration
        this.delegate = io.vertx.reactivex.ext.web.handler.CSPHandler.create().setReportOnly(TRUE.equals(isReportOnly));
        addDirectives(directives);
    }

    private void addDirectives(List<String> directives) {
        if (nonNull(directives) && directives.size() > 0) {
            directives.stream().map(directive -> directive.split("[ \t]", 2))
                    .filter(directive -> directive.length == 2)
                    .map(this::getDirectiveEntry)
                    .filter(e -> !e.getKey().isEmpty() && !e.getValue().isEmpty())
                    .forEach(e -> this.delegate.addDirective(e.getKey(), e.getValue()));
        }
    }

    private Entry<String, String> getDirectiveEntry(String[] directive) {
        var value = directive[1].trim();
        if (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1);
        }
        return Map.entry(directive[0].trim(), value.trim());
    }

    @Override
    public void handle(RoutingContext event) {
        this.delegate.handle(event);
    }
}
