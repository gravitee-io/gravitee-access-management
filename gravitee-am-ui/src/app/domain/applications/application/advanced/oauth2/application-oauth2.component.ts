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
import { ActivatedRoute, Router } from '@angular/router';

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'application-oauth2',
  templateUrl: './application-oauth2.component.html',
  styleUrls: ['./application-oauth2.component.scss'],
  standalone: false,
})
export class ApplicationOAuth2Component implements OnInit {
  protected domainId: string;
  private applicationId: string;
  application: any;
  oauthSettings: any = {};
  readonly = false;
  formChanged = false;

  // Data for shared components
  customGrantTypes: any[] = [];
  scopes: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.applicationId = this.application.id;

    // Extract oauth settings
    this.oauthSettings = this.application.settings?.oauth || {};

    // Load resolvers data
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'] || [];
    this.scopes = this.route.snapshot.data['scopes'] || [];

    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
  }

  updateSettings(newSettings: any) {
    this.oauthSettings = newSettings;
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }

  save() {
    // Validation for private_key_jwt
    if (this.oauthSettings.tokenEndpointAuthMethod === 'private_key_jwt') {
      if (!this.oauthSettings.jwksUri && !this.oauthSettings.jwks) {
        this.snackbarService.open("The jwks_uri or jwks are required when using 'private_key_jwt' client authentication method");
        return;
      }
      if (this.oauthSettings.jwksUri && this.oauthSettings.jwks) {
        this.snackbarService.open('The jwks_uri and jwks parameters MUST NOT be used together.');
        return;
      }
      if (this.oauthSettings.jwks) {
        try {
          if (typeof this.oauthSettings.jwks === 'string') {
            JSON.parse(this.oauthSettings.jwks);
          }
        } catch {
          this.snackbarService.open('The jwks parameter is malformed.');
          return;
        }
      }
    }

    const oauthSettings: any = { ...this.oauthSettings };
    if (oauthSettings.jwks && typeof oauthSettings.jwks === 'string') {
      oauthSettings.jwks = JSON.parse(oauthSettings.jwks);
    }

    this.applicationService.patch(this.domainId, this.applicationId, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
    });
  }
}
