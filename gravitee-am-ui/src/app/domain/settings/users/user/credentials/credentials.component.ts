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
import { MatDialog } from '@angular/material/dialog';
import { filter, switchMap, tap, mergeMap } from 'rxjs/operators';
import { forkJoin } from 'rxjs';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { AuthService } from '../../../../../services/auth.service';

import { CertificateEnrollmentDialogComponent } from './certificate-enrollment/certificate-enrollment-dialog.component';

@Component({
  selector: 'app-user-credentials',
  templateUrl: './credentials.component.html',
  styleUrls: ['./credentials.component.scss'],
  standalone: false,
})
export class UserCredentialsComponent implements OnInit {
  private domainId: string;
  private user: any;
  webauthnCredentials: any[] = [];
  certificateCredentials: any[] = [];
  allCredentials: any[] = [];
  canRevoke: boolean;
  canEnroll: boolean;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly snackbarService: SnackbarService,
    private readonly dialogService: DialogService,
    private readonly userService: UserService,
    private readonly authService: AuthService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.user = this.route.snapshot.data['user'];
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
    this.canEnroll = this.authService.hasPermissions(['domain_user_update']);

    // Load credentials from route data (for backward compatibility)
    const routeCredentials = this.route.snapshot.data['credentials'] || [];
    this.webauthnCredentials = routeCredentials;

    // Load all credentials (WebAuthn and Certificate)
    this.loadCredentials();
  }

  get isEmpty() {
    return this.allCredentials.length === 0;
  }

  remove(event, credential) {
    event.preventDefault();
    const isCertificate = credential.credentialType === 'certificate' || credential.certificatePem || credential.certificateThumbprint;
    const credentialType = isCertificate ? 'Certificate' : 'WebAuthn';

    this.dialogService
      .confirm(`Remove ${credentialType} credential`, 'Are you sure you want to remove this credential ?')
      .pipe(
        filter((res) => res),
        switchMap(() => {
          if (isCertificate) {
            return this.userService.removeCertificateCredential(this.domainId, this.user.id, credential.id);
          } else {
            return this.userService.removeCredential(this.domainId, this.user.id, credential.id);
          }
        }),
        tap(() => {
          this.snackbarService.open('Credential deleted');
          this.loadCredentials();
        }),
      )
      .subscribe();
  }

  loadCredentials() {
    forkJoin({
      webauthn: this.userService.credentials(this.domainId, this.user.id),
      certificates: this.userService.certificateCredentials(this.domainId, this.user.id),
    }).subscribe({
      next: ({ webauthn, certificates }) => {
        this.webauthnCredentials = webauthn || [];
        this.certificateCredentials = certificates || [];
        this.combineCredentials();
      },
      error: (_error: unknown) => {
        // Fallback to route data if API calls fail
        this.webauthnCredentials = this.route.snapshot.data['credentials'] || [];
        this.certificateCredentials = [];
        this.combineCredentials();
      },
    });
  }

  combineCredentials() {
    // Combine and mark credential types
    this.allCredentials = [
      ...this.webauthnCredentials.map((c) => ({ ...c, credentialType: 'webauthn' })),
      ...this.certificateCredentials.map((c) => ({ ...c, credentialType: 'certificate' })),
    ];
  }

  openCertificateEnrollmentDialog() {
    const dialogRef = this.dialog.open(CertificateEnrollmentDialogComponent, {
      width: '600px',
      data: {
        domainId: this.domainId,
        userId: this.user.id,
      },
    });

    dialogRef
      .afterClosed()
      .pipe(
        filter((result) => !!result),
        mergeMap((result) => this.userService.enrollCertificate(this.domainId, this.user.id, result.certificatePem, result.deviceName)),
        tap(() => {
          this.snackbarService.open('Certificate enrolled successfully');
          this.loadCredentials();
        }),
      )
      .subscribe({
        error: (error: unknown) => {
          const httpError = error as { error?: { message?: string }; message?: string };
          const errorMessage = httpError.error?.message || httpError.message || 'Failed to enroll certificate';
          this.snackbarService.open(errorMessage);
        },
      });
  }
}
