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
package io.gravitee.am.gateway.handler.vertx.view.thymeleaf;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeThymeleafTemplateEngine extends ThymeleafTemplateEngine {

    private DomainBasedThemeResolver themeResolver;

    public GraviteeThymeleafTemplateEngine(Vertx vertx) {
        super(ThymeleafTemplateEngine.create(vertx).getDelegate());
    }

    public void setThemeResolver(DomainBasedThemeResolver themeResolver) {
        this.themeResolver = themeResolver;
    }

    @Override
    public void render(Map<String, Object> context, String templateFileName, Handler<AsyncResult<Buffer>> handler) {
        themeResolver.resolveTheme(context);
        super.render(context, templateFileName, handler);
    }
}
