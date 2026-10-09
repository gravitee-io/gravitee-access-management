/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graviteesource.gamma.module.am.rest;

import com.graviteesource.gamma.module.am.sso.AmSsoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Mounted by the host at {@code /organizations/{orgId}/[environments/{envId}/]modules/am}.
 */
public class AmRootResource {

  @Inject
  private AmSsoService amSsoService;

  @Context
  private SecurityContext securityContext;

  /**
   * Returns the one-time AM console URL for the signed-in Gamma user. JSON rather than a 302, so the
   * console calls it with its usual credentials and then leaves with a plain top-level navigation.
   */
  @GET
  @Path("/sso")
  @Produces(MediaType.APPLICATION_JSON)
  public SsoResponse sso(@PathParam("orgId") String orgId) {
    return new SsoResponse(
      amSsoService.consoleLoginUrl(
        orgId,
        securityContext.getUserPrincipal().getName()
      )
    );
  }

  public record SsoResponse(String url) {}
}
