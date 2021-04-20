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
import { ActivatedRoute } from '@angular/router';
import { ProviderService } from '../../../services/provider.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { OrganizationService } from '../../../services/organization.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-settings-management-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss'],
})
export class ManagementGeneralComponent implements OnInit {
  settings: any;
  identityProviders: any[] = [];
  socialIdentities: any[] = [];
  readonly: boolean;

  constructor(
    private providerService: ProviderService,
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.settings = this.route.snapshot.data.settings;
    this.readonly = !this.authService.hasPermissions(['organization_settings_update']);
    this.organizationService.identityProviders().subscribe((data) => {
      // Separate all idps and all social idps.
      this.identityProviders = data.filter((idp) => !idp.external);
      this.socialIdentities = data.filter((idp) => idp.external);

      // Prepare the list of selected idps and social idps.
      this.settings.identityProviders = this.identityProviders
        .filter((idp) => !idp.external && this.settings.identities.includes(idp.id))
        .map((idp) => idp.id);
      this.settings.socialIdentities = this.socialIdentities
        .filter((idp) => idp.external && this.settings.identities.includes(idp.id))
        .map((idp) => idp.id);
    });
  }

  update() {
    const identities = this.settings.identityProviders.concat(this.settings.socialIdentities);
    this.organizationService.patchSettings({ identities }).subscribe((response) => {
      this.snackbarService.open('Settings updated');
    });
  }
}
