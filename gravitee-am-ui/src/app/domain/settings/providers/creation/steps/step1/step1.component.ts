/*
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
import { Component, OnInit, Input } from '@angular/core';
import { OrganizationService } from "../../../../../../services/organization.service";

@Component({
  selector: 'provider-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class ProviderCreationStep1Component implements OnInit {
  private identityProviderTypes: any = {
    'ldap-am-idp' : 'Generic LDAP / AD',
    'mongo-am-idp' : 'MongoDB',
    'inline-am-idp': 'Inline',
    'oauth2-generic-am-idp': 'OpenID Connect',
    'google-am-idp': 'Google',
    'azure-ad-am-idp': 'Azure AD',
    'twitter-am-idp': 'Twitter',
    'github-am-idp': 'GitHub',
    'facebook-am-idp': 'Facebook',
    'http-am-idp': 'HTTP',
    'saml2-generic-am-idp': 'SAML 2.0',
    'franceconnect-am-idp': 'FranceConnect',
    'jdbc-am-idp': 'JDBC'
  };
  @Input() provider;
  providers: any[];
  socialProviders: any[];
  selectedProviderTypeId: string;

  constructor(private organizationService: OrganizationService) {
  }

  ngOnInit() {
    this.organizationService.identities().subscribe(data => this.providers = data);
    this.organizationService.socialIdentities().subscribe(data => this.socialProviders = data);
  }

  selectProviderType(isExternal: boolean) {
    this.provider.external = isExternal;
    this.provider.type = this.selectedProviderTypeId;
  }

  displayName(idp) {
    if (this.identityProviderTypes[idp.id]) {
      return this.identityProviderTypes[idp.id];
    }
    return idp.name;
  }
}
