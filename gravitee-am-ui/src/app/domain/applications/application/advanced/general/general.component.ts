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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';
import { map, remove } from 'lodash';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { DialogService } from '../../../../../services/dialog.service';
import { AuthService } from '../../../../../services/auth.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

@Component({
  selector: 'application-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss'],
  standalone: false,
})
export class ApplicationGeneralComponent implements OnInit {
  @ViewChild('applicationForm', { static: true }) form: any;
  private domainId: string;
  domain: any;
  application: any;
  singleSignOut: boolean;
  silentReAuthentication: boolean;
  skipConsent: boolean;
  agentCardUrl: string;
  requestUri: string;
  redirectUri: string;
  logoutRedirectUri: string;
  requestUris: any[] = [];
  redirectUris: any[] = [];
  logoutRedirectUris: any[] = [];
  formChanged = false;
  editMode: boolean;
  deleteMode: boolean;
  applicationType: string;
  applicationTypes: any[] = [
    {
      name: 'Web',
      type: 'WEB',
    },
    {
      name: 'Single-Page App',
      type: 'BROWSER',
    },
    {
      name: 'Native',
      type: 'NATIVE',
    },
    {
      name: 'Agentic Application',
      type: 'AGENT',
    },
    {
      name: 'Backend to Backend',
      type: 'SERVICE',
    },
    {
      name: 'Resource Server',
      type: 'RESOURCE_SERVER',
    },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private authService: AuthService,
    private dialogService: DialogService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.domainId = this.domain.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.applicationType = this.application.type.toUpperCase();
    this.redirectUris = this.application.settings.oauth.redirectUris || [];
    this.requestUris = this.application.settings.oauth.requestUris || [];
    this.singleSignOut = this.application.settings.oauth.singleSignOut;
    this.silentReAuthentication = this.application.settings.oauth.silentReAuthentication;
    this.skipConsent = this.application.settings.advanced.skipConsent;
    this.agentCardUrl = this.application.settings.advanced.agentCardUrl;
    this.application.factors = this.application.factors || [];
    this.redirectUris = map(this.redirectUris, function (item) {
      return { value: item };
    });
    this.requestUris = map(this.requestUris, function (item) {
      return { value: item };
    });
    this.logoutRedirectUris = map(this.application.settings.oauth.postLogoutRedirectUris, function (item) {
      return { value: item };
    });
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.deleteMode = this.authService.hasPermissions(['application_settings_delete']);
    if (!this.domain.uma || !this.domain.uma.enabled) {
      remove(this.applicationTypes, { type: 'RESOURCE_SERVER' });
    }
  }

  update() {
    const data: any = {};
    data.name = this.application.name;
    data.description = this.application.description;
    data.settings = {};
    data.settings.oauth = {
      redirectUris: map(this.redirectUris, 'value'),
      requestUris: map(this.requestUris, 'value'),
      postLogoutRedirectUris: map(this.logoutRedirectUris, 'value'),
      singleSignOut: this.singleSignOut,
      silentReAuthentication: this.silentReAuthentication,
    };
    data.settings.advanced = {
      skipConsent: this.skipConsent,
      agentCardUrl: this.agentCardUrl?.trim() ?? '',
    };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(() => {
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Application', 'Are you sure you want to delete this application ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.applicationService.delete(this.domainId, this.application.id)),
        tap(() => {
          this.snackbarService.open('Application deleted');
          this.router.navigate(['/environments', this.domain.referenceId, 'domains', this.domain.id, 'applications']);
        }),
      )
      .subscribe();
  }

  changeApplicationType() {
    this.dialogService
      .confirm('Change application type', 'Are you sure you want to change the type of the application ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.applicationService.updateType(this.domainId, this.application.id, this.applicationType)),
        tap((data) => {
          this.application = data;
          this.snackbarService.open('Application type changed');
        }),
        switchMap(() => this.router.navigateByUrl('/dummy', { skipLocationChange: true })),
        switchMap(() =>
          this.router.navigate(['/environments', this.domain.referenceId, 'domains', this.domain.id, 'applications', this.application.id]),
        ),
      )
      .subscribe();
  }

  addRedirectUris(event) {
    event.preventDefault();
    if (this.redirectUri) {
      const sanitizedUri = this.redirectUri.trim();
      if (!this.redirectUris.some((el) => el.value === sanitizedUri)) {
        this.redirectUris.push({ value: sanitizedUri });
        this.redirectUris = [...this.redirectUris];
        this.redirectUri = null;
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : redirect URI "${sanitizedUri}" already exists`);
      }
    }
  }

  addRequestUris(event) {
    event.preventDefault();
    if (this.requestUri) {
      const sanitizedUri = this.requestUri.trim();
      if (!this.requestUris.some((el) => el.value === sanitizedUri)) {
        this.requestUris.push({ value: sanitizedUri });
        this.requestUris = [...this.requestUris];
        this.requestUri = null;
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : request URI "${sanitizedUri}" already exists`);
      }
    }
  }

  addLogoutRedirectUris(event) {
    event.preventDefault();
    if (this.logoutRedirectUri) {
      const sanitizedUri = this.logoutRedirectUri.trim();
      if (!this.logoutRedirectUris.some((el) => el.value === sanitizedUri)) {
        this.logoutRedirectUris.push({ value: sanitizedUri });
        this.logoutRedirectUris = [...this.logoutRedirectUris];
        this.logoutRedirectUri = null;
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : redirect URI "${sanitizedUri}" already exists`);
      }
    }
  }

  deleteRedirectUris(redirectUri, event) {
    event.preventDefault();
    this.dialogService.confirm('Remove redirect URI', 'Are you sure you want to remove this redirect URI ?').subscribe((res) => {
      if (res) {
        remove(this.redirectUris, { value: redirectUri });
        this.redirectUris = [...this.redirectUris];
        this.formChanged = true;
      }
    });
  }

  deleteRequestUris(requestUri, event) {
    event.preventDefault();
    this.dialogService.confirm('Remove request URI', 'Are you sure you want to remove this request URI ?').subscribe((res) => {
      if (res) {
        remove(this.requestUris, { value: requestUri });
        this.requestUris = [...this.requestUris];
        this.formChanged = true;
      }
    });
  }

  deleteLogoutRedirectUris(logoutRedirectUri, event) {
    event.preventDefault();
    this.dialogService.confirm('Remove logout redirect URI', 'Are you sure you want to remove this redirect URI ?').subscribe((res) => {
      if (res) {
        remove(this.logoutRedirectUris, { value: logoutRedirectUri });
        this.logoutRedirectUris = [...this.logoutRedirectUris];
        this.formChanged = true;
      }
    });
  }

  enableAutoApprove(event) {
    this.skipConsent = event.checked;
    this.formChanged = true;
  }

  enableSingleSignOut(event) {
    this.singleSignOut = event.checked;
    this.formChanged = true;
  }

  enableSilentReAuthentication(event) {
    this.silentReAuthentication = event.checked;
    this.formChanged = true;
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  displaySection(): boolean {
    return this.application.type !== 'service';
  }

  elRedirectUriEnabled(): boolean {
    return this.domain?.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage;
  }
}
