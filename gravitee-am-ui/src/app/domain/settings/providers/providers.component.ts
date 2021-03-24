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
import { Component, OnInit } from '@angular/core';
import { ProviderService } from "../../../services/provider.service";
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import {ActivatedRoute, Router} from "@angular/router";
import {OrganizationService} from "../../../services/organization.service";

@Component({
  selector: 'app-providers',
  templateUrl: './providers.component.html',
  styleUrls: ['./providers.component.scss']
})
export class DomainSettingsProvidersComponent implements OnInit {
  private providers: any[];
  private identities: any[];
  private organizationContext = false;
  domainId: string;

  constructor(private providerService: ProviderService,
              private organizationService: OrganizationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain'].id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.providers = this.route.snapshot.data['providers'];
    this.identities = this.route.snapshot.data['identities'];
  }

  loadProviders() {
    if (this.organizationContext) {
      this.organizationService.identityProviders().subscribe(response => this.providers = response);
    } else {
      this.providerService.findByDomain(this.domainId).subscribe(response => this.providers = response);
    }
  }

  get isEmpty() {
    return !this.providers || this.providers.length === 0;
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

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Provider', 'Are you sure you want to delete this provider ?')
      .subscribe(res => {
        if (res) {
          this.providerService.delete(this.domainId, id, this.organizationContext).subscribe(response => {
            this.snackbarService.open('Provider deleted');
            this.loadProviders();
          });
        }
      });
  }

}
