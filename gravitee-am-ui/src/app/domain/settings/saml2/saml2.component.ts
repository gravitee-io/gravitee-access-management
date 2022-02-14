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
import {Component, OnInit, ViewChild} from '@angular/core';
import {SnackbarService} from "../../../services/snackbar.service";
import {ActivatedRoute} from "@angular/router";
import {DomainService} from "../../../services/domain.service";
import {AuthService} from "../../../services/auth.service";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {CertificateService} from "../../../services/certificate.service";

@Component({
  selector: 'app-saml2',
  templateUrl: './saml2.component.html',
  styleUrls: ['./saml2.component.scss']
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

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private certificateService: CertificateService,
              private route: ActivatedRoute,
              public dialog: MatDialog) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.certificates = this.route.snapshot.data['certificates'];
    this.domainSamlSettings = this.domain.saml || {};
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_saml_update']);
    if (this.domainSamlSettings.certificate) {
      this.publicKeys(this.domainSamlSettings.certificate);
    }
  }

  save() {
    this.domainSamlSettings.certificate = (this.domainSamlSettings.certificate) ? this.domainSamlSettings.certificate : null;
    this.domainService.patch(this.domainId, {'saml' : this.domainSamlSettings}).subscribe(data => {
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
    this.certificateService.publicKeys(this.domainId, certificateId).subscribe(response => {
      this.certificatePublicKeys = response;
    });
  }
}
