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
import { NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, map, switchMap, tap } from 'rxjs/operators';

import { ProviderService } from '../../../../../services/provider.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { OrganizationService } from '../../../../../services/organization.service';
import { DomainService } from '../../../../../services/domain.service';
import { DialogService } from '../../../../../services/dialog.service';
import { EntrypointService } from '../../../../../services/entrypoint.service';
import { AppConfig } from '../../../../../../config/app.config';
import { enrichOIDCFormWithCerts } from '../provider.oidc.enricher';

@Component({
  selector: 'provider-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss'],
})
export class ProviderSettingsComponent implements OnInit {
  @ViewChild('providerForm', { static: true }) public form: NgForm;
  private domainId: string;
  organizationContext = false;
  domain: any = {};
  entrypoint: any = {};
  configurationIsValid = true;
  configurationPristine = true;
  providerSchema: any;
  provider: any;
  providerConfiguration: any;
  updateProviderConfiguration: any;
  redirectUri: string;
  customCode: string;
  domainWhitelistPattern: string;
  certificates: any[];

  constructor(
    private providerService: ProviderService,
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService,
    private dialogService: DialogService,
    private entrypointService: EntrypointService,
  ) {}

  ngOnInit() {
    this.certificates = this.route.snapshot.data['certificates'];
    this.provider = this.route.snapshot.data['provider'];
    if (this.provider.system) {
      // settings tab is useless for system providers
      // define the mappers as default landing page in this case
      this.router.navigate(['../mappers'], { relativeTo: this.route });
    }
    this.customCode = '<a th:href="${authorizeUrls.get(\'' + this.provider.id + '\')}">SIGN IN WITH OAUTH2 PROVIDER</a>';
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    if (this.organizationContext) {
      this.organizationService.settings().subscribe((data) => (this.domain = data));
      this.entrypoint = { url: AppConfig.settings.baseURL };
      this.redirectUri = this.entrypoint.url + '/auth/login/callback?provider=' + this.provider.id;
    } else {
      this.domain = this.route.snapshot.data['domain'];
      this.domainId = this.domain.id;
      this.domainService.getEntrypoint(this.domainId).subscribe((data) => {
        this.entrypoint = data;
        this.redirectUri = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain) + '/login/callback';
      });
    }
    this.providerConfiguration = JSON.parse(this.provider.configuration);
    this.updateProviderConfiguration = this.providerConfiguration;
    this.organizationService
      .identitySchema(this.provider.type)
      .pipe(map((schema) => enrichOIDCFormWithCerts(schema, this.certificates)))
      .subscribe((data) => {
        this.providerSchema = data;
        if (data) {
          Object.keys(this.providerSchema['properties']).forEach((key) => {
            // Only apply default values for boolean properties to fix AM-686 and LDAP issues
            // This prevents overriding null values for non-boolean properties while still providing defaults for booleans
            if (
              this.providerSchema['properties'][key].default &&
              this.providerSchema['properties'][key].type === 'boolean' &&
              this.providerConfiguration[key] == null
            ) {
              this.providerConfiguration[key] = this.providerSchema['properties'][key].default;
            }
            this.providerSchema['properties'][key].default = '';
          });
        }
      });
  }

  update(event: Event): void {
    if (this.provider.type !== 'inline-am-idp') {
      this._update();
    } else {
      event.preventDefault();
      const originalConfig = JSON.parse(this.provider.configuration);
      const updatedUsernames = this.updateProviderConfiguration.users
        ? this.updateProviderConfiguration.users.map((user) => user.username)
        : [];
      const allOriginalUsernames = !originalConfig.users || originalConfig.users.every((u) => updatedUsernames.includes(u.username));

      if (!allOriginalUsernames) {
        const title = 'Update Provider: a user has been modified or deleted.';
        const message =
          'If you modified an existing user with another username make sure the password has been modified manually too. ' +
          'Do you want to save your configuration ?';
        this.dialogService.confirm(title, message).subscribe((res) => {
          if (res) {
            this._update();
          }
        });
      } else {
        this._update();
      }
    }
  }

  private _update(): void {
    this.provider.configuration = this.updateProviderConfiguration;
    this.providerService.update(this.domainId, this.provider.id, this.provider, this.organizationContext).subscribe((data) => {
      this.provider = data;
      this.providerConfiguration = JSON.parse(this.provider.configuration);
      this.updateProviderConfiguration = this.providerConfiguration;
      this.snackbarService.open('Provider updated');
      this.configurationPristine = true;
      this.form.reset(data);
    });
  }

  delete(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Provider', 'Are you sure you want to delete this provider ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.providerService.delete(this.domainId, this.provider.id, this.organizationContext)),
        tap(() => {
          this.snackbarService.open('Identity provider deleted');
          this.router.navigate(['../..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  enableProviderUpdate(configurationWrapper: any): void {
    window.setTimeout(() => {
      this.configurationPristine = this.provider.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateProviderConfiguration = configurationWrapper.configuration;
    });
  }

  addDomainWhitelistPattern(event: Event): void {
    event.preventDefault();
    if (this.domainWhitelistPattern) {
      if (!this.provider.domainWhitelist.some((el) => el === this.domainWhitelistPattern)) {
        this.provider.domainWhitelist.push(this.domainWhitelistPattern);
        this.provider.domainWhitelist = [...this.provider.domainWhitelist];
        this.form.form.markAsDirty();
        this.domainWhitelistPattern = '';
      } else {
        this.snackbarService.open(`Error : domain whitelist pattern "${this.domainWhitelistPattern}" already exists`);
      }
    }
  }

  removeDomainWhitelistPattern(dwPattern: string): void {
    const index = this.provider.domainWhitelist.indexOf(dwPattern);
    if (index > -1) {
      this.provider.domainWhitelist.splice(index, 1);
      this.form.form.markAsDirty();
    }
  }

  valueCopied(message: string): void {
    this.snackbarService.open(message);
  }
}
