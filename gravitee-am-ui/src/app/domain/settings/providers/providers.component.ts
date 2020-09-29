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
import { AppConfig } from "../../../../config/app.config";
import {OrganizationService} from "../../../services/organization.service";

@Component({
  selector: 'app-providers',
  templateUrl: './providers.component.html',
  styleUrls: ['./providers.component.scss']
})
export class DomainSettingsProvidersComponent implements OnInit {
  private providers: any[];
  private organizationContext = false;
  private identityProviderTypes: any = {
    'ldap-am-idp' : 'LDAP / AD',
    'mongo-am-idp' : 'MongoDB',
    'inline-am-idp': 'Inline',
    'oauth2-generic-am-idp': 'OpenID Connect',
    'github-am-idp': 'GitHub',
    'azure-ad-am-idp': 'Azure AD',
    'facebook-am-idp': 'Facebook'
  };
  private identityProviderIcons: any = {
    'ldap-am-idp' : 'device_hub',
    'mongo-am-idp' : 'storage',
    'inline-am-idp': 'insert_drive_file',
    'oauth2-generic-am-idp': 'cloud_queue',
    'github-am-idp': 'cloud_queue',
    'azure-ad-am-idp': 'cloud_queue',
    'facebook-am-idp': 'cloud_queue'
  };
  domainId: string;

  constructor(private providerService: ProviderService,
              private organizationService: OrganizationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.providers = this.route.snapshot.data['providers'];
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

  getIdentityProviderTypeIcon(type) {
    if (this.identityProviderIcons[type]) {
      return this.identityProviderIcons[type];
    }
    return 'storage';
  }

  displayType(type) {
    if (this.identityProviderTypes[type]) {
      return this.identityProviderTypes[type];
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
