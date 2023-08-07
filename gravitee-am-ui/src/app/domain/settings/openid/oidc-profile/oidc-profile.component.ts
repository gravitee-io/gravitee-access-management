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

import { AuthService } from '../../../../services/auth.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-oidc-profile',
  templateUrl: './oidc-profile.component.html',
  styleUrls: ['./oidc-profile.component.scss'],
})
export class OIDCProfileComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
  }

  save() {
    this.domainService.patchOpenidDCRSettings(this.domainId, this.domain).subscribe((data) => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('OpenID Profile configuration updated');
    });
  }

  enableFAPI(event) {
    if (!this.domain.oidc.securityProfileSettings) {
      this.domain.oidc.securityProfileSettings = {};
    }
    this.domain.oidc.securityProfileSettings.enablePlainFapi = event.checked;
    if (!event.checked) {
      // Disable Plain FAPI imply to disable FAPI Brazil
      this.domain.oidc.securityProfileSettings.enableFapiBrazil = event.checked;
    }
    this.formChanged = true;
  }

  isFAPIEnabled() {
    return this.domain.oidc.securityProfileSettings && this.domain.oidc.securityProfileSettings.enablePlainFapi;
  }

  enableFAPIBrazil(event) {
    if (!this.domain.oidc.securityProfileSettings) {
      this.domain.oidc.securityProfileSettings = {};
    }
    this.domain.oidc.securityProfileSettings.enableFapiBrazil = event.checked;
    if (event.checked) {
      // Enable FAPI Brazil imply to enable Plain FAPI
      this.domain.oidc.securityProfileSettings.enablePlainFapi = event.checked;
    }
    this.formChanged = true;
  }

  isFAPIBrazilEnabled() {
    return this.domain.oidc.securityProfileSettings && this.domain.oidc.securityProfileSettings.enableFapiBrazil;
  }
}
