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
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../services/snackbar.service';
import { DomainService } from '../../../services/domain.service';
import { AuthService } from '../../../services/auth.service';
import { CertificateService } from '../../../services/certificate.service';
import { AmFeature } from '../../../components/gio-license/gio-license-data';
import { DomainStoreService } from '../../../stores/domain.store';

@Component({
  selector: 'app-saml2',
  templateUrl: './saml2.component.html',
  styleUrls: ['./saml2.component.scss'],
})
export class Saml2Component implements OnInit {
  @ViewChild('samlSettingsForm', { static: true }) form: any;
  domainId: string;
  domain: any = {};
  domainSamlSettings: any;
  formChanged = false;
  editMode: boolean;
  certificates: any[] = [];
  certificatePublicKeys: any[] = [];
  saml2LicenseOptions: LicenseOptions = { feature: AmFeature.AM_IDP_GATEWAY_HANDLER_SAML };
  isMissingSaml2Feature$: Observable<boolean>;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private certificateService: CertificateService,
    private route: ActivatedRoute,
    private licenseService: GioLicenseService,
    private domainStore: DomainStoreService,
    public dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.certificates = this.route.snapshot.data['certificates'];
    this.domainSamlSettings = this.domain.saml || {};
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_saml_update']);
    if (this.domainSamlSettings.certificate) {
      this.publicKeys(this.domainSamlSettings.certificate);
    }

    this.isMissingSaml2Feature$ = this.licenseService.isMissingFeature$(this.saml2LicenseOptions.feature);
  }

  save() {
    this.domainSamlSettings.certificate = this.domainSamlSettings.certificate ? this.domainSamlSettings.certificate : null;
    this.domainService.patch(this.domainId, { saml: this.domainSamlSettings }).subscribe((data) => {
      this.domainStore.set(data);
      this.domain['saml'] = data.saml;
      this.formChanged = false;
      this.form.reset(this.domain.saml);
      this.snackbarService.open('SAML 2.0 configuration updated');
    });
  }

  onChange(event) {
    this.formChanged = true;
    if (event.value) {
      this.publicKeys(event.value);
    } else {
      this.certificatePublicKeys = [];
    }
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  enableSAML2IdP(event) {
    this.domainSamlSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isSAML2IdPEnabled() {
    return this.domainSamlSettings.enabled;
  }

  private publicKeys(certificateId) {
    this.certificateService.publicKeys(this.domainId, certificateId).subscribe((response) => {
      this.certificatePublicKeys = response;
    });
  }
}
