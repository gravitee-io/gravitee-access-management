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
package io.gravitee.am.gateway.handler.common.vertx.web;

import io.vertx.ext.web.Session;
import io.vertx.ext.web.impl.RoutingContextInternal;
import io.vertx.ext.web.impl.UserContextInternal;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralizes access to Vert.x 5 internal APIs for setting users and sessions on routing contexts.
 *
 * Vert.x 5 removed the public setUser/setSession methods from RoutingContext.
 * The only way to set them is via internal implementation classes (UserContextInternal, RoutingContextInternal).
 * Vert.x's own AuthenticationHandlerImpl uses the same pattern, so this is effectively stable.
 *
 * All call sites should use this helper instead of casting directly.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoutingContextHelper {

    public static void setUser(RoutingContext ctx, io.gravitee.am.model.User user) {
        setUser(ctx, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
    }

    public static void setUser(RoutingContext ctx, io.vertx.ext.auth.User user) {
        ((UserContextInternal) ctx.getDelegate().userContext()).setUser(user);
    }

    public static void setSession(RoutingContext ctx, Session session) {
        ((RoutingContextInternal) ctx.getDelegate()).setSession(session);
    }
}
