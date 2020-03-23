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
import {ActivatedRoute, Router} from "@angular/router";
import {DomainService} from "../../../../../services/domain.service";
import {DialogService} from "../../../../../services/dialog.service";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {AuthService} from "../../../../../services/auth.service";

@Component({
  selector: 'app-openid-client-registration-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class ClientRegistrationSettingsComponent implements OnInit {
  formChanged = false;
  domain: any = {};
  clientDcrDisabled = false;
  disableToolTip = false;
  toolTipMessage = '';
  readonly: boolean;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.readonly = !this.authService.hasPermissions(['domain_dcr_create', 'domain_dcr_update']);
  }

  enableDynamicClientRegistration(event) {
    this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled = event.checked;
    // If disabled, ensure to disable open dynamic client registration too and disable clients toggle too.
    if (!event.checked) {
      this.domain.oidc.clientRegistrationSettings.isOpenDynamicClientRegistrationEnabled = event.checked;
      this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled = event.checked;

      this.clientDcrDisabled = !event.checked;
      this.disableToolTip = event.checked;
      this.toolTipMessage = 'Disable until settings are saved and feature is enabled.';
    }
    this.formChanged = true;
  }

  enableOpenDynamicClientRegistration(event) {
    this.domain.oidc.clientRegistrationSettings.isOpenDynamicClientRegistrationEnabled = event.checked;
    // If enabled, ensure to enable dynamic client registration too.
    if (event.checked) {
      this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled = event.checked;
    }
    this.formChanged = true;
  }

  enableDynamicClientRegistrationTemplate(event) {
    this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled = event.checked;
    // If enabled, ensure to enable dynamic client registration too.
    if (event.checked) {
      this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled = event.checked;
    }
    this.formChanged = true;
  }

  allowLocalhostRedirectUri(event) {
    this.domain.oidc.clientRegistrationSettings.allowLocalhostRedirectUri = event.checked;
    this.formChanged = true;
  }

  allowHttpSchemeRedirectUri(event) {
    this.domain.oidc.clientRegistrationSettings.allowHttpSchemeRedirectUri = event.checked;
    this.formChanged = true;
  }

  allowWildCardRedirectUri(event) {
    this.domain.oidc.clientRegistrationSettings.allowWildCardRedirectUri = event.checked;
    this.formChanged = true;
  }

  enableRedirectUriStrictMatching(event) {
    this.domain.oidc.redirectUriStrictMatching = event.checked;
    this.formChanged = true;
  }

  patch() {
    this.domainService.patchOpenidDCRSettings(this.domain.id, this.domain).subscribe(response => {
      this.domain = response;
      this.domainService.notify(this.domain);
      this.snackbarService.open('Domain ' + this.domain.name + ' updated');
      this.formChanged = false;
    });
  }
}
