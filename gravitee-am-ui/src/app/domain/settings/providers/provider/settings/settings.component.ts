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
import { Observable } from 'rxjs';
import { filter, map, shareReplay, switchMap, tap } from 'rxjs/operators';
import { LicenseOptions } from '@gravitee/ui-particles-angular';

import { ProviderService } from '../../../../../services/provider.service';
import { PluginFeatureService } from '../../../../../services/plugin-feature.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { OrganizationService } from '../../../../../services/organization.service';
import { DomainService } from '../../../../../services/domain.service';
import { DialogService } from '../../../../../services/dialog.service';
import { EntrypointService } from '../../../../../services/entrypoint.service';
import { AppConfig } from '../../../../../../config/app.config';
import {
  enrichFormWithCerts,
  enrichFormWithSystemClusterDisclaimer,
  enrichFormWithSystemClusterLock,
  enrichFormWithSystemClusterRestrictions,
  MONGO_IDP_TYPE,
  PINNED_STORAGE_FIELDS,
  PINNED_STORAGE_TOGGLE,
} from '../provider.form.enricher';
import { DataSourcesService } from '../../../../../services/datasources.service';
import { CloudModeService } from '../../../../../services/cloud-mode.service';

@Component({
  selector: 'provider-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss'],
  standalone: false,
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
  licenseOptions: LicenseOptions = {};
  isMissingFeature$: Observable<boolean>;
  private systemClusterRestricted = false;
  private datasources: any[];

  constructor(
    private providerService: ProviderService,
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService,
    private dialogService: DialogService,
    private entrypointService: EntrypointService,
    private dataSourcesService: DataSourcesService,
    private pluginFeatureService: PluginFeatureService,
    private cloudModeService: CloudModeService,
  ) {}

  ngOnInit() {
    this.certificates = this.route.snapshot.data['certificates'];
    this.datasources = this.route.snapshot.data['datasources'];
    this.provider = this.route.snapshot.data['provider'];
    this.pluginFeatureService
      .getFeature$('identity_provider', this.provider.type)
      .subscribe((feature) => (this.licenseOptions = { feature }));
    this.isMissingFeature$ = this.pluginFeatureService
      .isMissingFeatureForType$('identity_provider', this.provider.type)
      .pipe(shareReplay({ bufferSize: 1, refCount: true }));
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
    this.cloudModeService
      .isSystemClusterRestricted()
      .pipe(
        switchMap((restricted) =>
          this.organizationService.identitySchema(this.provider.type).pipe(
            map((schema) => enrichFormWithCerts(schema, this.certificates)),
            map((schema) => enrichFormWithSystemClusterRestrictions(schema, this.provider.type, this.provider.systemClusterRestricted)),
            map((schema) => enrichFormWithSystemClusterLock(schema, this.provider.type, restricted)),
            map((schema) => enrichFormWithSystemClusterDisclaimer(schema, this.provider.type, restricted)),
            tap(() => (this.systemClusterRestricted = restricted)),
          ),
        ),
      )
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
          // Process datasource widgets
          this.providerSchema = this.dataSourcesService.applyDataSourceSelection(this.providerSchema, this.datasources);
        }
      });
  }

  get showMongoStorageGuidance(): boolean {
    return this.provider?.type === MONGO_IDP_TYPE;
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
      const configuration = this.withPinnedStorage(configurationWrapper.configuration);
      this.configurationPristine = this.provider.configuration === JSON.stringify(configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateProviderConfiguration = configuration;
    });
  }

  /**
   * Angular leaves a readonly control out of the form value, so a field the form shows closed would
   * reach the server as undefined and read as a change. Send back what is stored.
   */
  private withPinnedStorage(configuration: any): any {
    if (typeof this.provider.configuration !== 'string') {
      return configuration;
    }
    const closed = this.provider.systemClusterRestricted
      ? [PINNED_STORAGE_TOGGLE, ...PINNED_STORAGE_FIELDS]
      : this.systemClusterRestricted
        ? [PINNED_STORAGE_TOGGLE]
        : [];
    if (closed.length === 0) {
      return configuration;
    }
    const stored = JSON.parse(this.provider.configuration);
    const pinned = {};
    closed.forEach((field) => {
      pinned[field] = stored[field];
    });
    return { ...configuration, ...pinned };
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
