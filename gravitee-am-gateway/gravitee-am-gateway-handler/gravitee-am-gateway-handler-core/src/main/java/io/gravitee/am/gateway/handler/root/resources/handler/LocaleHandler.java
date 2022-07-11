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
package io.gravitee.am.gateway.handler.root.resources.handler;

import io.gravitee.am.gateway.handler.vertx.view.thymeleaf.GraviteeMessageResolver;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Locale;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LocaleHandler implements Handler<RoutingContext> {

    private final GraviteeMessageResolver messageResolver;

    public LocaleHandler(GraviteeMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // first evaluate AcceptedLanguage raw values
        for(var language : routingContext.acceptableLanguages()) {
            if (messageResolver.isSupported(new Locale(language.value()))) {
                routingContext.put("lang", language.value());
                routingContext.next();
                return;
            }
        }

        // if no raw values match, evaluate only the language code
        for(var language : routingContext.acceptableLanguages()) {
            if (messageResolver.isSupported(new Locale(language.tag()))) {
                routingContext.put("lang", language.tag());
                routingContext.next();
                return;
            }
        }

        routingContext.next();
    }
}
