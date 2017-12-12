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
package io.gravitee.am.gateway.handler.oauth2.provider.endpoint;

import io.gravitee.am.gateway.handler.oauth2.service.DomainScopeService;
import io.gravitee.am.model.oauth2.Scope;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.Approval;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
@SessionAttributes("authorizationRequest")
public class ScopeApprovalEndpoint {

    private ClientDetailsService clientDetailsService;

    private ApprovalStore approvalStore;

    private DomainScopeService scopeService;

    @RequestMapping("/oauth/confirm_access")
    public ModelAndView getAccessConfirmation(Map<String, Object> model, HttpServletRequest request, Principal principal) throws Exception {
        AuthorizationRequest clientAuth = (AuthorizationRequest) model.remove("authorizationRequest");
        model.put("auth_request", clientAuth);
        if (request.getAttribute("_csrf") != null) {
            model.put("_csrf", request.getAttribute("_csrf"));
        }


        ClientDetails client = clientDetailsService.loadClientByClientId(clientAuth.getClientId());
        model.put("client", client);

        Set<Scope> scopes = scopeService.getAll();
        Set<ClientScope> requestedScopes = new HashSet<>();

        // Set scopes asked by the client
        for (String requestScope : clientAuth.getScope()) {
            scopes
                    .stream()
                    .filter(scope -> scope.getKey().equalsIgnoreCase(requestScope))
                    .distinct()
                    .forEach(scope -> requestedScopes.add(new ClientScope(scope)));
        }

        // Get scope approvals
        for (Approval approval : approvalStore.getApprovals(principal.getName(), client.getClientId())) {
            if (clientAuth.getScope().contains(approval.getScope())) {
                requestedScopes
                        .stream()
                        .filter(scope -> scope.getKey().equalsIgnoreCase(approval.getScope()))
                        .distinct()
                        .forEach(clientScope -> clientScope.setChecked(approval.getStatus() == Approval.ApprovalStatus.APPROVED));
            }
        }

        model.put("scopes", requestedScopes);
        return new ModelAndView("access_confirmation", model);
    }

    @RequestMapping("/oauth/error")
    public String handleError(Map<String, Object> model) throws Exception {
        // We can add more stuff to the model here for JSP rendering. If the client was a machine then
        // the JSON will already have been rendered.
        model.put("message", "There was a problem with the OAuth2 protocol");
        return "access_error";
    }

    public void setClientDetailsService(ClientDetailsService clientDetailsService) {
        this.clientDetailsService = clientDetailsService;
    }

    public void setApprovalStore(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
    }

    public void setScopeService(DomainScopeService scopeService) {
        this.scopeService = scopeService;
    }

    private static class ClientScope {
        private String key;
        private String name;
        private String description;
        private boolean checked;

        ClientScope (final Scope scope) {
            this.key = scope.getKey();
            this.name = scope.getName();
            this.description = scope.getDescription();
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClientScope that = (ClientScope) o;

            return key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
