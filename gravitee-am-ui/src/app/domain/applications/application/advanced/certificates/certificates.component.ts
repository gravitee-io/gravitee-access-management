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
import {ActivatedRoute} from "@angular/router";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {ApplicationService} from "../../../../../services/application.service";
import {CertificateService} from "../../../../../services/certificate.service";

@Component({
  selector: 'app-application-certificates',
  templateUrl: './certificates.component.html',
  styleUrls: ['./certificates.component.scss']
})
export class ApplicationCertificatesComponent implements OnInit {
  private domainId: string;
  formChanged: boolean = false;
  application: any;
  certificates: any[] = [];
  certificatePublicKey: string;
  selectedCertificate: string;

  constructor(private route: ActivatedRoute,
              private snackbarService: SnackbarService,
              private applicationService: ApplicationService,
              private certificateService: CertificateService) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.certificates = this.route.snapshot.data['certificates'];
    if (this.application.certificate) {
      this.selectedCertificate = this.application.certificate;
      this.publicKey(this.application.certificate);
    }
  }

  patch(): void {
    let data = {'certificate': (this.selectedCertificate) ? this.selectedCertificate : null };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(data => {
      this.application = data;
      this.route.snapshot.data['application'] = this.application;
      this.certificatePublicKey = null;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      if (this.application.certificate) {
        this.publicKey(this.application.certificate);
      }
    });
  }

  onChange(event) {
    this.formChanged = true;
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  private publicKey(certificateId) {
    this.certificateService.publicKey(this.domainId, certificateId).subscribe(response => {
      this.certificatePublicKey = response;
    });
  }
}
