package io.gravitee.am.gateway.handler.management.api.resources;

import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.UpdateDomain;
import io.gravitee.am.gateway.service.model.UpdateLoginForm;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "login"})
public class DomainLoginFormResource {

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Custom login form", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response getDomainLoginForm(@PathParam("domain") String domainId) throws DomainNotFoundException {
        Domain domain = domainService.findById(domainId);
        if (domain.getLoginForm() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(domain.getLoginForm()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Set custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Custom login form successfully updated", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public LoginForm updateDomainLoginForm(
            @PathParam("domain") String domain,
            @ApiParam(name = "loginForm", required = true) @Valid @NotNull final UpdateLoginForm loginForm) {
        return domainService.updateLoginForm(domain, loginForm);
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Custom login form successfully deleted", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response deleteDomainLoginForm(
            @PathParam("domain") String domain) {
        domainService.deleteLoginForm(domain);

        return Response.noContent().build();
    }
}
