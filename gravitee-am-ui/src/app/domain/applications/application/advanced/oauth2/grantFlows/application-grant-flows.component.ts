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

import { ApplicationService } from '../../../../../../services/application.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';
import { AuthService } from '../../../../../../services/auth.service';
import { DomainStoreService } from '../../../../../../stores/domain.store';

@Component({
  selector: 'application-grant-types',
  templateUrl: './application-grant-flows.component.html',
  styleUrls: ['./application-grant-flows.component.scss'],
  standalone: false,
})
export class ApplicationGrantFlowsComponent implements OnInit {
  private domainId: string;
  formChanged: boolean;
  application: any;
  customGrantTypes: any[];
  applicationOauthSettings: any = {};
  readonly = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'] || [];
    this.applicationOauthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
  }

  patch() {
    // check configuration
    if (this.applicationOauthSettings.tokenEndpointAuthMethod === 'private_key_jwt') {
      if (!this.applicationOauthSettings.jwksUri && !this.applicationOauthSettings.jwks) {
        this.snackbarService.open("The jwks_uri or jwks are required when using 'private_key_jwt' client authentication method");
        return;
      }
      if (this.applicationOauthSettings.jwksUri && this.applicationOauthSettings.jwks) {
        this.snackbarService.open('The jwks_uri and jwks parameters MUST NOT be used together.');
        return;
      }
      if (this.applicationOauthSettings.jwks) {
        try {
          if (typeof this.applicationOauthSettings.jwks === 'string') {
            JSON.parse(this.applicationOauthSettings.jwks);
          }
        } catch {
          this.snackbarService.open('The jwks parameter is malformed.');
          return;
        }
      }
    }

    const oauthSettings: any = { ...this.applicationOauthSettings };
    if (oauthSettings.jwks && typeof oauthSettings.jwks === 'string') {
      oauthSettings.jwks = JSON.parse(oauthSettings.jwks);
    }

    this.applicationService.patch(this.domainId, this.application.id, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
    });
  }

  updateSettings(settings: any) {
    this.applicationOauthSettings = settings;
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }
}
