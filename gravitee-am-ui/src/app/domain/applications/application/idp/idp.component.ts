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
import {Component, OnInit} from '@angular/core';
import {SnackbarService} from "../../../../services/snackbar.service";
import {ActivatedRoute} from "@angular/router";
import {ProviderService} from "../../../../services/provider.service";
import {ApplicationService} from "../../../../services/application.service";
import {AuthService} from "../../../../services/auth.service";

@Component({
  selector: 'app-idp',
  templateUrl: './idp.component.html',
  styleUrls: ['./idp.component.scss']
})
export class ApplicationIdPComponent implements OnInit {
  private domainId: string;
  private identities: any;
  loadIdentities = true;
  application: any;
  identityProviders: any[];
  socialIdentityProviders: any[];
  formChanged = false;
  readonly = false;

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private providerService: ProviderService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.data['domain'].id;
    this.application = this.route.snapshot.data['application'];
    this.identities = this.route.snapshot.data['identities'];
    this.application.identities = this.application.identities || [];
    this.readonly = !this.authService.hasPermissions(['application_identity_provider_update']);
    this.providerService.findByDomain(this.domainId).subscribe(data => {
      this.identityProviders = data.filter(idp => !idp.external);
      this.socialIdentityProviders = data.filter(idp => idp.external);
      this.loadIdentities = false;
    });
  }

  update() {
    this.applicationService.patch(this.domainId, this.application.id, { 'identities': this.application.identities}).subscribe(data => {
      this.application = data;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
    });
  }

  selectIdentityProvider(event, identityProviderId) {
    if (event.checked) {
      this.application.identities.push(identityProviderId);
    } else {
      this.application.identities.splice(this.application.identities.indexOf(identityProviderId), 1);
    }
    this.formChanged = true;
  }

  isIdentityProviderSelected(identityProviderId) {
    return this.application.identities !== undefined && this.application.identities.includes(identityProviderId);
  }

  hasIdentityProviders() {
    return this.identityProviders && this.identityProviders.length > 0;
  }

  hasSocialIdentityProviders() {
    return this.socialIdentityProviders && this.socialIdentityProviders.length > 0;
  }

  getIdentityProvider(type) {
    if (this.identities && this.identities[type]) {
      return this.identities[type];
    }
    return null;
  }

  getIdentityProviderTypeIcon(type) {
    const provider = this.getIdentityProvider(type);
    if (provider && provider.icon) {
      const name = provider.displayName ? provider.displayName : provider.name;
      return `<img width="24" height="24" src="${provider.icon}" alt="${name} image" title="${name}"/>`;
    }
    return `<span class="material-icons">storage</span>`;
  }

  displayType(type) {
    const provider = this.getIdentityProvider(type);
    if (provider) {
      return provider.displayName ? provider.displayName : provider.name;
    }
    return 'Custom';
  }
}
