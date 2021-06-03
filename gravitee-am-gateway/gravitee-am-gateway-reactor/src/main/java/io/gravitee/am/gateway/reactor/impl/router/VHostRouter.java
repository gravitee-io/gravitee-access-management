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
package io.gravitee.am.gateway.reactor.impl.router;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.core.MultiMap;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Domain router for a particular vhost.
 * Vhost is optional, meaning routing is made only on domain path.
 * If vhost is defined, routing is made on both vhost and path (matching condition must be true).
 * If multiple vhosts have to be defined for the same domain, it is necessary to defined multiple {@link VHostRouter}s.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VHostRouter implements Router {

    private final Domain domain;
    private final Pattern vhostPattern;
    private final Pattern pathPattern;
    private final Router delegate;
    private final VirtualHost vhost;

    public static io.vertx.reactivex.ext.web.Router router(Domain domain, VirtualHost vhost, io.vertx.reactivex.ext.web.Router delegate) {

        return io.vertx.reactivex.ext.web.Router.newInstance(new VHostRouter(domain, vhost, delegate));
    }

    public static io.vertx.reactivex.ext.web.Router router(Domain domain, io.vertx.reactivex.ext.web.Router delegate) {

        return io.vertx.reactivex.ext.web.Router.newInstance(new VHostRouter(domain, delegate));
    }

    private VHostRouter(Domain domain, io.vertx.reactivex.ext.web.Router delegate) {

        this(domain, delegate.getDelegate());
    }

    private VHostRouter(Domain domain, Router delegate) {

        this.domain = domain;
        this.vhost = null;
        this.vhostPattern = null;
        this.pathPattern = null;
        this.delegate = delegate;
    }

    private VHostRouter(Domain domain, VirtualHost vhost, io.vertx.reactivex.ext.web.Router delegate) {

        this(domain, vhost, delegate.getDelegate());
    }

    private VHostRouter(Domain domain, VirtualHost vhost, Router delegate) {

        this.domain = domain;
        this.vhost = vhost;
        this.vhostPattern = Pattern.compile(Pattern.quote(vhost.getHost()));
        this.pathPattern = Pattern.compile(Pattern.quote(vhost.getPath()) + ".*");
        this.delegate = delegate;
    }

    @Override
    public void handleContext(RoutingContext context) {

        if (routerMatches(context)) {
            if (vhost != null) {
                setContextPath(context, vhost.getPath());
            } else {
                setContextPath(context, domain.getPath());
            }
            delegate.handleContext(context);
        } else {
            context.next();
        }
    }

    @Override
    public void handleFailure(RoutingContext context) {

        if (routerMatches(context)) {
            delegate.handleFailure(context);
        } else {
            context.next();
        }
    }

    private boolean routerMatches(RoutingContext context) {

        return vhost == null || (hostMatches(context) && pathMatches(context));
    }

    private boolean pathMatches(RoutingContext context) {

        return pathPattern == null || pathPattern.matcher(context.request().path()).matches();
    }

    private boolean hostMatches(RoutingContext context) {

        return vhostPattern == null || vhostPattern.matcher(context.request().host()).matches();
    }

    private void setContextPath(RoutingContext context, String contextPath) {

        if(contextPath.equals("/")) {
            // Set to empty to easily chain context path with other sub paths.
            context.put(CONTEXT_PATH, "");
        } else {
            context.put(CONTEXT_PATH, contextPath);
        }
    }

    @Override
    public Route route() {
        return delegate.route();
    }

    @Override
    public Route route(HttpMethod method, String path) {
        return delegate.route(method, path);
    }

    @Override
    public Route route(String path) {
        return delegate.route(path);
    }

    @Override
    public Route routeWithRegex(HttpMethod method, String regex) {
        return delegate.routeWithRegex(method, regex);
    }

    @Override
    public Route routeWithRegex(String regex) {
        return delegate.routeWithRegex(regex);
    }

    @Override
    public Route get() {
        return delegate.get();
    }

    @Override
    public Route get(String path) {
        return delegate.get(path);
    }

    @Override
    public Route getWithRegex(String regex) {
        return delegate.getWithRegex(regex);
    }

    @Override
    public Route head() {
        return delegate.head();
    }

    @Override
    public Route head(String path) {
        return delegate.head(path);
    }

    @Override
    public Route headWithRegex(String regex) {
        return delegate.headWithRegex(regex);
    }

    @Override
    public Route options() {
        return delegate.options();
    }

    @Override
    public Route options(String path) {
        return delegate.options(path);
    }

    @Override
    public Route optionsWithRegex(String regex) {
        return delegate.optionsWithRegex(regex);
    }

    @Override
    public Route put() {
        return delegate.put();
    }

    @Override
    public Route put(String path) {
        return delegate.put(path);
    }

    @Override
    public Route putWithRegex(String regex) {
        return delegate.putWithRegex(regex);
    }

    @Override
    public Route post() {
        return delegate.post();
    }

    @Override
    public Route post(String path) {
        return delegate.post(path);
    }

    @Override
    public Route postWithRegex(String regex) {
        return delegate.postWithRegex(regex);
    }

    @Override
    public Route delete() {
        return delegate.delete();
    }

    @Override
    public Route delete(String path) {
        return delegate.delete(path);
    }

    @Override
    public Route deleteWithRegex(String regex) {
        return delegate.deleteWithRegex(regex);
    }

    @Override
    public Route trace() {
        return delegate.trace();
    }

    @Override
    public Route trace(String path) {
        return delegate.trace(path);
    }

    @Override
    public Route traceWithRegex(String regex) {
        return delegate.traceWithRegex(regex);
    }

    @Override
    public Route connect() {
        return delegate.trace();
    }

    @Override
    public Route connect(String path) {
        return delegate.trace(path);
    }

    @Override
    public Route connectWithRegex(String regex) {
        return delegate.traceWithRegex(regex);
    }

    @Override
    public Route patch() {
        return delegate.patch();
    }

    @Override
    public Route patch(String path) {
        return delegate.patch(path);
    }

    @Override
    public Route patchWithRegex(String regex) {
        return delegate.patch(regex);
    }

    @Override
    public List<Route> getRoutes() {
        return delegate.getRoutes();
    }

    @Override
    public Router clear() {
        return delegate.clear();
    }

    @Override
    public Route mountSubRouter(String mountPoint, Router subRouter) {
        return delegate.mountSubRouter(mountPoint, subRouter);
    }

    @Override
    public Router errorHandler(int statusCode, Handler<RoutingContext> errorHandler) {
        return delegate.errorHandler(statusCode, errorHandler);
    }

    @Override
    public Router modifiedHandler(Handler<Router> handler) {
        return delegate.modifiedHandler(handler);
    }

    @Override
    public Router allowForward(AllowForwardHeaders allowForwardHeaders) {
        return delegate.allowForward(allowForwardHeaders);
    }

    @Override
    public void handle(HttpServerRequest event) {
        delegate.handle(event);
    }
}
