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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouterImpl;

import java.util.List;
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
 * <p>This router instead holds all the candidates for a given path in a flat list and resolves
 * the matching one with a simple iteration, so the request-handling stack depth no longer grows
 * with the number of domains/vhosts sharing that path.
 *
 * @author GraviteeSource Team
 */
public class VHostGroupRouter extends RouterImpl {

    private final List<VHostRouter> members = new CopyOnWriteArrayList<>();

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
     * must be kept to later {@link #removeMember(Object)} it (e.g. on domain undeploy).
     */
    public Object addMember(io.vertx.rxjava3.core.Vertx vertx, Domain domain, VirtualHost vhost, io.vertx.rxjava3.ext.web.Router delegate) {
        VHostRouter member = VHostRouter.member(vertx.getDelegate(), domain, vhost, delegate.getDelegate());
        members.add(member);
        return member;
    }

    public Object addMember(io.vertx.rxjava3.core.Vertx vertx, Domain domain, io.vertx.rxjava3.ext.web.Router delegate) {
        VHostRouter member = VHostRouter.member(vertx.getDelegate(), domain, delegate.getDelegate());
        members.add(member);
        return member;
    }

    public void removeMember(Object member) {
        if (member instanceof VHostRouter vHostRouter) {
            members.remove(vHostRouter);
        }
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    @Override
    public void handleContext(RoutingContext context) {
        for (VHostRouter member : members) {
            if (member.matches(context)) {
                member.dispatch(context);
                return;
            }
        }
        context.next();
    }

    @Override
    public void handleFailure(RoutingContext context) {
        for (VHostRouter member : members) {
            if (member.matches(context)) {
                member.dispatchFailure(context);
                return;
            }
        }
        context.next();
    }
}
