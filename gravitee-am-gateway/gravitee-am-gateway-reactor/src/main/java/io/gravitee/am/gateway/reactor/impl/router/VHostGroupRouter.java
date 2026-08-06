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
import io.vertx.core.Vertx;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouterImpl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates every {@link VHostRouter} that shares the same mount path onto a single
 * {@link Router} entry.
 *
 * <p>Vert.x's {@code RoutingContext.next()} walks the parent router's route list recursively:
 * each non-matching sub-router calls {@code context.next()}, which recurses into the following
 * one. When many domains/vhosts are deployed on the same path (differentiated only by the Host
 * header), mounting one {@link VHostRouter} per domain/vhost therefore produces one recursive
 * Java stack frame per candidate, which can blow the stack once a few thousand domains share a
 * path.
 *
 * <p>This router instead resolves the matching candidate for a given path in O(1): host-bound
 * members (regular vhosts) are indexed in a {@link Map} keyed by their literal, lower-cased host
 * (host matching is an exact comparison, see {@link VHostRouter#hostKey()}), so a request is
 * resolved with a single lookup instead of a scan. Host-agnostic members (non-vhost-mode
 * domains) are kept in a small fallback list, checked only when no host-bound member matches.
 *
 * @author GraviteeSource Team
 */
public class VHostGroupRouter extends RouterImpl {

    private final Map<String, VHostRouter> byHost = new ConcurrentHashMap<>();
    private final List<VHostRouter> hostAgnostic = new CopyOnWriteArrayList<>();

    private VHostGroupRouter(Vertx vertx) {
        super(vertx);
    }

    public static VHostGroupRouter create(io.vertx.rxjava3.core.Vertx vertx) {
        return new VHostGroupRouter(vertx.getDelegate());
    }

    public io.vertx.rxjava3.ext.web.Router asRxRouter() {
        return io.vertx.rxjava3.ext.web.Router.newInstance(this);
    }

    /**
     * Registers a new domain/vhost candidate on this group and returns an opaque handle that
     * must be kept to later {@link #removeMember(VHostRouter)} it (e.g. on domain undeploy).
     */
    public VHostRouter addMember(io.vertx.rxjava3.core.Vertx vertx, Domain domain, VirtualHost vhost, io.vertx.rxjava3.ext.web.Router delegate) {
        return register(VHostRouter.member(vertx.getDelegate(), domain, vhost, delegate.getDelegate()));
    }

    public VHostRouter addMember(io.vertx.rxjava3.core.Vertx vertx, Domain domain, io.vertx.rxjava3.ext.web.Router delegate) {
        return register(VHostRouter.member(vertx.getDelegate(), domain, delegate.getDelegate()));
    }

    private VHostRouter register(VHostRouter member) {
        String hostKey = member.hostKey();
        if (hostKey != null) {
            // First registration for a given host wins, matching the previous first-match
            // semantics of the sequential scan this class replaces.
            byHost.putIfAbsent(hostKey, member);
        } else {
            hostAgnostic.add(member);
        }
        return member;
    }

    public void removeMember(VHostRouter member) {
        String hostKey = member.hostKey();
        if (hostKey != null) {
            byHost.computeIfPresent(hostKey, (key, current) -> current == member ? null : current);
        } else {
            hostAgnostic.remove(member);
        }
    }

    public boolean isEmpty() {
        return byHost.isEmpty() && hostAgnostic.isEmpty();
    }

    @Override
    public void handleContext(RoutingContext context) {
        dispatchTo(resolve(context), context, false);
    }

    @Override
    public void handleFailure(RoutingContext context) {
        dispatchTo(resolve(context), context, true);
    }

    private void dispatchTo(VHostRouter member, RoutingContext context, boolean failure) {
        if (member == null) {
            context.next();
        } else if (failure) {
            member.dispatchFailure(context);
        } else {
            member.dispatch(context);
        }
    }

    private VHostRouter resolve(RoutingContext context) {
        String host = requestHost(context);
        VHostRouter candidate = host == null ? null : byHost.get(host);
        if (candidate != null && candidate.matches(context)) {
            return candidate;
        }

        for (VHostRouter member : hostAgnostic) {
            if (member.matches(context)) {
                return member;
            }
        }

        return null;
    }

    private String requestHost(RoutingContext context) {
        HostAndPort authority = context.request().authority();
        return authority == null ? null : authority.toString().toLowerCase(Locale.ROOT);
    }
}
