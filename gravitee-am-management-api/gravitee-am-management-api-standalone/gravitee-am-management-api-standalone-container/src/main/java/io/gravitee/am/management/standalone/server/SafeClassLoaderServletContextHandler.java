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
package io.gravitee.am.management.standalone.server;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;

/**
 * A {@link ServletContextHandler} that tolerates threads which block
 * {@link Thread#setContextClassLoader} (e.g. JDK {@code InnocuousThread}).
 *
 * <p>Jetty's {@link org.eclipse.jetty.server.handler.ContextHandler} calls
 * {@code setContextClassLoader} in both {@code enterScope} and {@code exitScope}
 * during request dispatch. When an async response ({@code AsyncResponse.resume()})
 * completes on an {@code InnocuousThread} — created by the JDK for NIO async
 * channel callbacks used by the MongoDB driver — the {@code setContextClassLoader}
 * call throws a {@code SecurityException}, which breaks the response.
 *
 * <p>This subclass catches and ignores the {@code SecurityException} in both
 * methods. This is safe because the management API runs as a single webapp
 * with no classloader isolation between contexts.
 *
 * @author GraviteeSource Team
 */
class SafeClassLoaderServletContextHandler extends ServletContextHandler {

    SafeClassLoaderServletContextHandler(String contextPath, int options) {
        super(contextPath, options);
    }

    @Override
    protected ClassLoader enterScope(Request request) {
        try {
            return super.enterScope(request);
        } catch (SecurityException e) {
            // InnocuousThread blocks setContextClassLoader — return current
            // classloader so exitScope has something to restore (even though
            // that restore will also be caught).
            return Thread.currentThread().getContextClassLoader();
        }
    }

    @Override
    protected void exitScope(Request request, Context previousContext, ClassLoader previousClassLoader) {
        try {
            super.exitScope(request, previousContext, previousClassLoader);
        } catch (SecurityException e) {
            // InnocuousThread blocks setContextClassLoader — safe to ignore
            // since the thread doesn't allow classloader changes anyway.
        }
    }
}
