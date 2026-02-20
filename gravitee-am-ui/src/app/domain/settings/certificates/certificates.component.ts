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
import { ActivatedRoute } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { filter, switchMap, tap } from 'rxjs/operators';

import { DialogService } from '../../../services/dialog.service';
import { CertificateService } from '../../../services/certificate.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DomainService } from '../../../services/domain.service';

import { CertificateSettingsDialogComponent, CertificateSettingsDialogResult } from './dialog/certificate-settings.component';

@Component({
  selector: 'app-certificates',
  templateUrl: './certificates.component.html',
  styleUrls: ['./certificates.component.scss'],
  standalone: false,
})
export class DomainSettingsCertificatesComponent implements OnInit {
  private certificateTypes: any = {
    'javakeystore-am-certificate': 'Java Keystore (.jks)',
    'pkcs12-am-certificate': 'PKCS#12 (.p12)',
    'aws-am-certificate': 'AWS Secret Manager',
    'aws-hsm-am-certificate': 'AWS CloudHSM',
  };
  private certificateIcons: any = {
    'javakeystore-am-certificate': 'security',
    'pkcs12-am-certificate': 'security',
    'aws-am-certificate': 'security',
  };
  private certificateUsage = new Map([
    ['enc', 'Encryption'],
    ['sig', 'Signature'],
    ['mtls', 'mTLS'],
  ]);

  certificates: any[];
  domain: any;
  domainId: string;
  threshold: number;
  ongoingRotation = false;

  constructor(
    private certificateService: CertificateService,
    private domainService: DomainService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain?.id;
    this.certificates = this.route.snapshot.data['certificates'];
  }

  get isEmpty() {
    return !this.certificates || this.certificates.length === 0;
  }

  loadCertificates() {
    this.certificateService.findByDomain(this.domainId).subscribe((response) => (this.certificates = response));
  }

  publicKey(id, event) {
    event.preventDefault();
    this.certificateService.publicKeys(this.domainId, id).subscribe(
      (response) => {
        this.openPublicKeyInfo(response, false);
      },
      () => {
        this.openPublicKeyInfo([], true);
      },
    );
  }

  getCertificateTypeIcon(type) {
    if (this.certificateIcons[type]) {
      return this.certificateIcons[type];
    }
    return 'security';
  }

  displayType(type) {
    if (this.certificateTypes[type]) {
      return this.certificateTypes[type];
    }
    return type;
  }

  usageBadgeLabel(cert: any): string {
    if (cert?.usage?.length > 1) {
      return `${cert.usage.length} usages`;
    } else if (cert?.usage?.length == 1) {
      return this.certificateUsage.get(cert.usage[0]);
    } else {
      return 'Undefined';
    }
  }

  usageBadgeTooltip(cert: any): string {
    return cert.usage.map((u) => this.certificateUsage.get(u)).join(', ');
  }

  certificateWillExpire(cert) {
    return cert.status === 'will_expire';
  }

  certificateIsExpired(cert) {
    return cert.status === 'expired';
  }

  certificateIsRenewed(cert) {
    return cert.status === 'renewed';
  }

  computeUsageLabel(label: string, values: any[]): string {
    const count = values ? values.length : 0;
    return `${count} ${label}` + (count != 1 ? 's' : '');
  }

  getUsageNames(values: any[]) {
    const names = values ? values.map((x) => x.name).join(' / ') : [];
    const length = values.length;
    return length > 4 ? names + ' / ...' : names;
  }

  expireInDays(expiry) {
    return Math.ceil((expiry - Date.now()) / (1000 * 3600 * 24));
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Certificate', 'Are you sure you want to delete this certificate ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.certificateService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Certificate deleted');
          this.loadCertificates();
        }),
      )
      .subscribe();
  }

  openPublicKeyInfo(publicKeys, error) {
    const dialogRef = this.dialog.open(CertitificatePublicKeyDialogComponent, { width: '700px' });
    dialogRef.componentInstance.title = 'Public certificate key';
    dialogRef.componentInstance.certificateKeys = publicKeys;
    dialogRef.componentInstance.error = error;
  }

  openSettings() {
    const dialogRef = this.dialog.open(CertificateSettingsDialogComponent, {
      width: '500px',
      data: {
        certificates: this.certificates,
        selectedCertificateId: this.domain?.certificateSettings?.fallbackCertificate || null,
      },
    });

    dialogRef
      .afterClosed()
      .pipe(
        filter((result: CertificateSettingsDialogResult) => result && result.action === 'confirm'),
        switchMap((result: CertificateSettingsDialogResult) => {
          const certificateSettings = {
            fallbackCertificate: result.selectedCertificateId,
          };
          return this.domainService.updateCertificateSettings(this.domainId, certificateSettings).pipe(
            tap(() => {
              this.snackbarService.open('Certificate settings updated');
              this.domain.certificateSettings = certificateSettings;
            }),
          );
        }),
      )
      .subscribe();
  }

  rotateCertificate() {
    this.dialogService
      .confirm(
        'Rotate key',
        'Are you sure you want to generate a new system certificate?\nThis will only affect the latest System Certificate created.',
      )
      .pipe(
        filter((res) => res),
        tap(() => (this.ongoingRotation = true)),
        switchMap(() => this.certificateService.rotateCertificate(this.domainId)),
        tap(() => {
          this.snackbarService.open('New system certificate created');
          this.loadCertificates();
          this.ongoingRotation = false;

          // expiration date is extract during the certificate plugin generation
          // we trigger a 5 second delay refresh to try provide the information
          // without user interaction
          setTimeout(() => {
            this.loadCertificates();
          }, 5000);
        }),
      )
      .subscribe();
  }

  preventRotateCertificate() {
    return this.ongoingRotation;
  }

  isFallbackCertificate(certificateId: string): boolean {
    return this.domain?.certificateSettings?.fallbackCertificate === certificateId;
  }
}

@Component({
  selector: 'certificate-public-key-dialog',
  templateUrl: './dialog/public-key.component.html',
  styleUrls: ['./dialog/public-key.component.scss'],
  standalone: false,
})
export class CertitificatePublicKeyDialogComponent {
  public title: string;
  public certificateKeys: any[] = [];
  public error: boolean;

  constructor(
    public dialogRef: MatDialogRef<CertitificatePublicKeyDialogComponent>,
    private snackbarService: SnackbarService,
  ) {}

  valueCopied(message) {
    this.snackbarService.open(message);
  }
}
