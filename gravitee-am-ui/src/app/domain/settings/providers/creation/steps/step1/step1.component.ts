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
import { ActivatedRoute } from '@angular/router';

import { OrganizationService } from '../../../../../../services/organization.service';
import { IdentityProvider } from '../../../../../../entities/identity-providers/IdentityProvider';

@Component({
  selector: 'provider-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
})
export class ProviderCreationStep1Component implements OnInit {
  identities: IdentityProvider[];
  @Input() provider;
  filter: string;
  filteredIdentities: IdentityProvider[];

  constructor(private organizationService: OrganizationService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.filter = '';
    this.identities = this.route.snapshot.data['identities'];
    this.filteredIdentities = this.getFilteredIdentities();
  }

  private initDomainWhitelist(idpType: string) {
    this.provider.domainWhitelist = [];
    if (idpType === 'google-am-idp') {
      this.provider.domainWhitelist.push('gmail.com');
    }
    if (idpType === 'azure-ad-am-idp') {
      this.provider.domainWhitelist.push('microsoft.com');
    }
  }

  selectProviderType(idp: IdentityProvider) {
    this.provider.external = idp.external === true;
    this.provider.type = idp.id;
    this.initDomainWhitelist(idp.id);
  }

  displayName(identityProvider) {
    return identityProvider.displayName ? identityProvider.displayName : identityProvider.name;
  }

  getIcon(identityProvider) {
    if (identityProvider && identityProvider.icon) {
      const title = identityProvider.displayName ? identityProvider.displayName : identityProvider.name;
      return `<img mat-card-avatar src="${identityProvider.icon}" alt="${title} image" title="${title}"/>`;
    }
    return `<i class="material-icons">storage</i>`;
  }

  private getFilteredIdentities() {
    const identities = Object.values(this.identities);
    if (this.filter != null && this.filter.trim().length > 0) {
      return identities.filter((identity) => {
        let fields = [identity.name.toLowerCase()];
        if (identity.displayName != null) {
          fields.push(identity.displayName.toLowerCase());
        }
        if (identity.labels != null) {
          fields = [...fields, ...identity.labels.map((t) => t.toLowerCase().toLowerCase())];
        }
        return JSON.stringify(fields).includes(this.filter.toLowerCase());
      });
    }
    return identities;
  }

  clear() {
    this.filter = '';
  }

  onFilterChange() {
    this.filteredIdentities = this.getFilteredIdentities();
  }
}
