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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users;

import io.gravitee.am.gateway.handler.scim.UserService;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * Clients request resource removal via DELETE.  Service providers MAY
 *    choose not to permanently delete the resource but MUST return a 404
 *    (Not Found) error code for all operations associated with the
 *    previously deleted resource.  Service providers MUST omit the
 *    resource from future query results.  In addition, the service
 *    provider SHOULD NOT consider the deleted resource in conflict
 *    calculation.  For example, if a User resource is deleted, a CREATE
 *    request for a User resource with the same userName as the previously
 *    deleted resource SHOULD NOT fail with a 409 error due to userName
 *    conflict.
 *
 * In response to a successful DELETE, the server SHALL return a
 *    successful HTTP status code 204 (No Content).
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.6>3.6. Deleting Resources</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeleteUserEndpointHandler implements Handler<RoutingContext> {

    private UserService userService;

    public DeleteUserEndpointHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String userId = context.request().getParam("id");
        userService.delete(userId)
                .subscribe(
                        () -> context.response().setStatusCode(204).end(),
                        error -> context.fail(error));
    }

    public static DeleteUserEndpointHandler create(UserService userService) {
        return new DeleteUserEndpointHandler(userService);
    }
}
