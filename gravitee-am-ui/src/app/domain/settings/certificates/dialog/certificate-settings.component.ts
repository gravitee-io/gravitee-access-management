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
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface CertificateSettingsDialogData {
  certificates: any[];
  selectedCertificateId: string | null;
}

export interface CertificateSettingsDialogResult {
  action: 'confirm' | 'cancel';
  selectedCertificateId: string | null;
}

@Component({
  selector: 'certificate-settings-dialog',
  templateUrl: './certificate-settings.component.html',
  styleUrls: ['./certificate-settings.component.scss'],
  standalone: false,
})
export class CertificateSettingsDialogComponent {
  certificates: any[] = [];
  selectedCertificateId: string | null = null;

  constructor(
    public dialogRef: MatDialogRef<CertificateSettingsDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: CertificateSettingsDialogData,
  ) {
    this.certificates = data.certificates || [];
    this.selectedCertificateId = data.selectedCertificateId || null;
  }

  cancel(): void {
    this.dialogRef.close({ action: 'cancel', selectedCertificateId: null });
  }

  clear(): void {
    this.selectedCertificateId = null;
  }

  confirm(): void {
    this.dialogRef.close({ action: 'confirm', selectedCertificateId: this.selectedCertificateId });
  }
}
