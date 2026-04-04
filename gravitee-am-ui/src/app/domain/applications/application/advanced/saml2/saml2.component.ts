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

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { AuthService } from '../../../../../services/auth.service';

interface ApplicationSaml2SettingsPayload {
  entityId?: string;
  attributeConsumeServiceUrl?: string;
  singleLogoutServiceUrl?: string;
  wantResponseSigned?: boolean;
  wantAssertionsSigned?: boolean;
  wantAssertionsEncrypted?: boolean;
  keyTransportEncryptionAlgorithm?: string;
  dataEncryptionAlgorithm?: string;
  certificate?: string;
  responseBinding?: string;
  nameIdMapping?: string;
  assertionAttributes?: { name: string; value: string }[];
}

@Component({
  selector: 'app-application-saml2',
  templateUrl: './saml2.component.html',
  styleUrls: ['./saml2.component.scss'],
  standalone: false,
})
export class ApplicationSaml2Component implements OnInit {
  @ViewChild('samlSettingsForm', { static: true }) form: any;
  private domainId: string;
  application: any;
  applicationSamlSettings: ApplicationSaml2SettingsPayload;
  formChanged: boolean;
  editMode: boolean;
  certificates: any[] = [];
  newAttributeName = '';
  newAttributeValue = '';
  bindings: any[] = [
    { name: 'Initial-Request', value: 'urn:oasis:names:tc:SAML:2.0:bindings:custom:Initial-Request' },
    { name: 'HTTP-POST', value: 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST' },
    { name: 'HTTP-Redirect', value: 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect' },
  ];

  /** XML Encryption URIs for RSA key transport (encrypted assertions). */
  rsaKeyTransportOptions: { name: string; value: string }[] = [
    { name: 'Default (IdP choice)', value: '' },
    { name: 'RSA-OAEP (MGF1)', value: 'http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p' },
    { name: 'RSA PKCS#1 v1.5', value: 'http://www.w3.org/2001/04/xmlenc#rsa-1_5' },
  ];

  /** XML Encryption URIs for block / data encryption. */
  dataEncryptionAlgorithmOptions: { name: string; value: string }[] = [
    { name: 'Default (IdP choice)', value: '' },
    { name: 'AES-128-CBC', value: 'http://www.w3.org/2001/04/xmlenc#aes128-cbc' },
    { name: 'AES-256-CBC', value: 'http://www.w3.org/2001/04/xmlenc#aes256-cbc' },
    { name: 'AES-256-GCM', value: 'http://www.w3.org/2009/xmlenc11#aes256-gcm' },
  ];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private applicationService: ApplicationService,
    private authService: AuthService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.certificates = this.route.snapshot.data['certificates'];
    this.applicationSamlSettings = this.application.settings == null ? {} : this.application.settings.saml || {};
    this.normalizeEncryptionSelects();
    this.editMode = this.authService.hasPermissions(['application_saml_update']);
  }

  private normalizeEncryptionSelects() {
    this.applicationSamlSettings.keyTransportEncryptionAlgorithm = this.applicationSamlSettings.keyTransportEncryptionAlgorithm ?? '';
    this.applicationSamlSettings.dataEncryptionAlgorithm = this.applicationSamlSettings.dataEncryptionAlgorithm ?? '';
  }

  patch() {
    const trimmedCert = this.applicationSamlSettings.certificate?.trim();
    this.applicationSamlSettings.certificate = trimmedCert ? trimmedCert : null;
    this.applicationSamlSettings.keyTransportEncryptionAlgorithm = this.applicationSamlSettings.keyTransportEncryptionAlgorithm
      ? this.applicationSamlSettings.keyTransportEncryptionAlgorithm
      : null;
    this.applicationSamlSettings.dataEncryptionAlgorithm = this.applicationSamlSettings.dataEncryptionAlgorithm
      ? this.applicationSamlSettings.dataEncryptionAlgorithm
      : null;
    const settings = {
      settings: {
        saml: this.applicationSamlSettings,
      },
    };
    this.applicationService.patch(this.domainId, this.application.id, settings).subscribe((data) => {
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
      this.application = data;
      this.applicationSamlSettings = data.settings == null ? {} : data.settings.saml || {};
      this.normalizeEncryptionSelects();
      this.form.reset(this.applicationSamlSettings);
      this.snackbarService.open('Application updated');
    });
  }

  responseBindingChanged(value: string) {
    this.applicationSamlSettings.responseBinding = value;
    this.formChanged = true;
  }

  addAssertionAttribute() {
    const name = this.newAttributeName?.trim();
    const value = this.newAttributeValue;
    if (!name || !value) return;
    const existing = (this.applicationSamlSettings.assertionAttributes || []).some((a) => a.name === name);
    if (existing) {
      this.snackbarService.open('Attribute name already exists');
      return;
    }
    this.applicationSamlSettings.assertionAttributes = [...(this.applicationSamlSettings.assertionAttributes || []), { name, value }];
    this.newAttributeName = '';
    this.newAttributeValue = '';
    this.formChanged = true;
  }

  deleteAssertionAttribute(index: number) {
    this.applicationSamlSettings.assertionAttributes = (this.applicationSamlSettings.assertionAttributes || []).filter(
      (_, i) => i !== index,
    );
    this.formChanged = true;
  }

  encryptionPreferenceChanged() {
    this.formChanged = true;
  }
}
