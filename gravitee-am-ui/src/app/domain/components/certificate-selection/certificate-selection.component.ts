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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

import { CertificateService } from '../../../services/certificate.service';
import { SnackbarService } from '../../../services/snackbar.service';

export interface Certificate {
  id: string;
  name: string;
}

export interface CertificatePublicKey {
  fmt: string;
  payload: string;
}

@Component({
  selector: 'app-certificate-selection',
  templateUrl: './certificate-selection.component.html',
  styleUrls: ['./certificate-selection.component.scss'],
  standalone: false,
})
export class CertificateSelectionComponent implements OnInit {
  @Input() domainId: string;
  @Input() certificates: Certificate[] = [];
  @Input() initialCertificateId: string;
  @Input() readonly = false;
  @Input() showSaveButton = false;
  @Input() description: string;
  @Input() emptyStateMessage: string;
  @Input() emptyStateSubMessage: string;

  @Output() certificateChange = new EventEmitter<string>();
  @Output() save = new EventEmitter<string>();
  @Output() copied = new EventEmitter<void>();

  selectedCertificate: string;
  certificatePublicKeys: CertificatePublicKey[] = [];
  formChanged = false;

  constructor(
    private certificateService: CertificateService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    if (this.initialCertificateId) {
      this.selectedCertificate = this.initialCertificateId;
      this.loadPublicKeys(this.initialCertificateId);
    }
  }

  onCertificateChange(): void {
    this.formChanged = true;
    this.certificatePublicKeys = [];
    if (this.selectedCertificate) {
      this.loadPublicKeys(this.selectedCertificate);
    }
    this.certificateChange.emit(this.selectedCertificate);
  }

  onSave(): void {
    this.formChanged = false;
    this.save.emit(this.selectedCertificate);
  }

  onCopied(): void {
    this.snackbarService.open('Certificate key copied to the clipboard');
    this.copied.emit();
  }

  private loadPublicKeys(certificateId: string): void {
    if (!this.domainId || !certificateId) {
      return;
    }
    this.certificateService.publicKeys(this.domainId, certificateId).subscribe((response) => {
      this.certificatePublicKeys = response;
    });
  }
}
