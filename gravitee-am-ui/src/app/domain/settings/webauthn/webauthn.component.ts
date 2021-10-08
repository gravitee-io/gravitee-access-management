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
import {Component, OnChanges, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {DomainService} from '../../../services/domain.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {EntrypointService} from '../../../services/entrypoint.service';
import * as _ from "lodash";

@Component({
  selector: 'app-domain-webauthn',
  templateUrl: './webauthn.component.html',
  styleUrls: ['./webauthn.component.scss']
})
export class DomainSettingsWebAuthnComponent implements OnInit {
  private entrypoint: any;
  private baseUrl: string;
  domainId: string;
  domain: any = {};
  formChanged = false;
  readonly = false;
  userVerifications: string[] = ['required', 'preferred', 'discouraged'];
  authenticatorAttachments: string[] = ['cross_platform', 'platform'];
  attestationConveyancePreferences: string[] = ['none', 'indirect', 'direct'];
  attestationNames: string[] = [ 'none', 'u2f', 'packed', 'android-key', 'android-safetynet', 'tpm', 'apple', 'mds' ];
  attestation: any = {};
  attestationCertificates: any[] = [];
  editing = {};

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              private entrypointService: EntrypointService) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain);
    this.domain.webAuthnSettings = this.domain.webAuthnSettings || {};
    this.domain.webAuthnSettings.certificates = this.domain.webAuthnSettings.certificates || {};
    const url = new URL(this.domain.webAuthnSettings.origin || this.baseUrl);
    this.domain.webAuthnSettings.origin = url.origin;
    this.domain.webAuthnSettings.relyingPartyId = this.domain.webAuthnSettings.relyingPartyId || url.hostname;
    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
    this.initCertificates();
  }

  initCertificates() {
    if (this.domain.webAuthnSettings.certificates) {
      _.forEach(this.domain.webAuthnSettings.certificates, (v, k) => {
        const attestation = {};
        attestation['id'] = Math.random().toString(36).substring(7);
        attestation['name'] = k;
        attestation['value'] = v;
        this.attestationCertificates.push(attestation);
      });
    }
  }

  save() {
    if (this.attestationCertificates) {
      let attestation = {};
      _.each(this.attestationCertificates, function (item) {
        attestation[item.name] = item.value;
      });
      this.domain.webAuthnSettings.certificates = attestation;
    }

    this.domainService.patchWebAuthnSettings(this.domainId, this.domain).subscribe(data => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('WebAuthn configuration updated');
    });
  }

  addCertificate(event) {
    event.preventDefault();
    if (!this.certificateExits(this.attestation.name)) {
      this.attestation.id = Math.random().toString(36).substring(7);
      this.attestationCertificates.push(this.attestation);
      this.attestationCertificates = [...this.attestationCertificates];
      this.formChanged = true;
      this.attestation = {};
    } else {
      this.snackbarService.open(`Error : metadata "${this.attestation.name}" already exists`);
    }
  }

  updateCertificate(event, cell, rowIndex) {
    let metadata = event.target.value;
    if (metadata) {
      if (cell === 'name' && this.certificateExits(metadata)) {
        this.snackbarService.open(`Error : attestation "${metadata}" already exists`);
        return;
      }
      this.editing[rowIndex + '-' + cell] = false;
      let index = _.findIndex(this.attestationCertificates, {id: rowIndex});
      this.attestationCertificates[index][cell] = metadata;
      this.attestationCertificates = [...this.attestationCertificates];
      this.formChanged = true;
    }
  }

  deleteCertificate(key, event) {
    event.preventDefault();
    _.remove(this.attestationCertificates, function(el) {
      return el.id === key;
    });
    this.attestationCertificates = [...this.attestationCertificates];
    this.formChanged = true;
  }

  certificateExits(attribute): boolean {
    return _.find(this.attestationCertificates, function(el) { return  el.name === attribute; })
  }

  certificatesIsEmpty() {
    return !this.attestationCertificates || Object.keys(this.attestationCertificates).length === 0;
  }

  updateFormState() {
    this.formChanged = true;
  }
}
