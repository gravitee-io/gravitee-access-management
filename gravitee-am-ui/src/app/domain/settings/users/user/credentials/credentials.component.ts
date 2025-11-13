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
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { filter, switchMap, tap, mergeMap, catchError, takeUntil } from 'rxjs/operators';
import { forkJoin, of, Subject } from 'rxjs';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { AuthService } from '../../../../../services/auth.service';
import { ApplicationService } from '../../../../../services/application.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

import { CertificateEnrollmentDialogComponent } from './certificate-enrollment/certificate-enrollment-dialog.component';

export interface WebAuthnCredential {
  id: string;
  credentialId: string;
  deviceName?: string;
  publicKey: string;
  attestationStatementFormat?: string;
  createdAt?: string;
  [key: string]: any;
}

export interface CertificateCredential {
  id: string;
  certificateSubjectDN?: string;
  certificateThumbprint?: string;
  certificatePem?: string;
  certificateExpiresAt?: string;
  deviceName?: string;
  createdAt?: string;
  [key: string]: any;
}

export const CREDENTIAL_TYPE = {
  WEBAUTHN: 'webauthn',
  CERTIFICATE: 'certificate',
} as const;

export const CREDENTIAL_DISPLAY_NAME = {
  WEBAUTHN: 'WebAuthn',
  CERTIFICATE: 'Certificate',
} as const;

export type Credential =
  | (WebAuthnCredential & { credentialType: typeof CREDENTIAL_TYPE.WEBAUTHN })
  | (CertificateCredential & { credentialType: typeof CREDENTIAL_TYPE.CERTIFICATE });

@Component({
  selector: 'app-user-credentials',
  templateUrl: './credentials.component.html',
  styleUrls: ['./credentials.component.scss'],
  standalone: false,
})
export class UserCredentialsComponent implements OnInit, OnDestroy {
  private domainId: string;
  private domain: any;
  private user: any;
  private application: any; // Store full application with settings
  private readonly destroy$ = new Subject<void>();
  allCredentials: Credential[] = [];
  canRevoke: boolean;

  // Expose constants to template
  readonly CREDENTIAL_TYPE = CREDENTIAL_TYPE;
  readonly CREDENTIAL_DISPLAY_NAME = CREDENTIAL_DISPLAY_NAME;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly snackbarService: SnackbarService,
    private readonly dialogService: DialogService,
    private readonly userService: UserService,
    private readonly authService: AuthService,
    private readonly applicationService: ApplicationService,
    private readonly dialog: MatDialog,
    private readonly domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.user = this.route.snapshot.data['user'];
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);

    // Subscribe to route data changes to get updated user data (e.g., when application settings change)
    this.route.data.pipe(takeUntil(this.destroy$)).subscribe((data) => {
      if (data['user']) {
        this.user = data['user'];
        // Reset application to trigger reload if user changed
        this.application = null;
        this.loadApplicationIfNeeded();
      }
    });

    // Subscribe to domain store for live updates (e.g., when CBA flag is enabled)
    // Following the same pattern as login.component.ts
    this.domainStore.domain$.pipe(takeUntil(this.destroy$)).subscribe((domain) => {
      if (domain && domain.id === this.domainId) {
        this.domain = deepClone(domain);
      } else if (!this.domain) {
        // Fallback to route snapshot if store doesn't have domain yet
        this.domain = this.route.snapshot.data['domain'];
      }
    });

    // Load application if user has applicationEntity
    this.loadApplicationIfNeeded();

    this.loadCredentials();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadApplicationIfNeeded() {
    // Fetch full application with settings if user has applicationEntity
    if (this.user?.applicationEntity?.id && !this.application) {
      this.applicationService
        .get(this.domainId, this.user.applicationEntity.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (application) => {
            this.application = application;
          },
          error: () => {
            // Silently fail - will fall back to domain settings
            // Application fetch failure is not critical as we can use domain settings
          },
        });
    }
  }

  get canEnroll() {
    return this.authService.hasPermissions(['domain_user_update']) && this.isCertificateBasedAuthEnabled();
  }

  isCertificateBasedAuthEnabled(): boolean {
    // Check application-level login settings first (if user belongs to an application and it's not inherited)
    // Following the exact same pattern as allowResetPassword() in application emails component
    // Use the fetched application object which has full settings, not user.applicationEntity
    const appLoginSettings = this.application?.settings?.login;

    if (appLoginSettings && !appLoginSettings.inherited) {
      // When application has explicit login settings (not inherited), use application-level setting
      // If the property is missing, it defaults to false (opt-in behavior)
      return appLoginSettings.certificateBasedAuthEnabled === true;
    }

    // Fall back to domain-level login settings
    return this.domain?.loginSettings?.certificateBasedAuthEnabled === true;
  }

  get isEmpty() {
    return this.allCredentials.length === 0;
  }

  remove(event, credential: Credential) {
    event.preventDefault();
    const credentialType =
      credential.credentialType === CREDENTIAL_TYPE.CERTIFICATE ? CREDENTIAL_DISPLAY_NAME.CERTIFICATE : CREDENTIAL_DISPLAY_NAME.WEBAUTHN;

    this.dialogService
      .confirm(`Remove ${credentialType} credential`, 'Are you sure you want to remove this credential ?')
      .pipe(
        filter((res) => res),
        switchMap(() => {
          if (credential.credentialType === CREDENTIAL_TYPE.CERTIFICATE) {
            return this.userService.removeCertificateCredential(this.domainId, this.user.id, credential.id);
          } else {
            return this.userService.removeCredential(this.domainId, this.user.id, credential.id);
          }
        }),
        tap(() => {
          this.snackbarService.open('Credential deleted');
          this.loadCredentials();
        }),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  loadCredentials() {
    forkJoin({
      webauthn: this.userService.credentials(this.domainId, this.user.id).pipe(
        catchError((error: unknown) => {
          this.snackbarService.open('Failed to load WebAuthn credentials');
          console.error('Error loading WebAuthn credentials:', error);
          return of([]);
        }),
      ),
      certificates: this.userService.certificateCredentials(this.domainId, this.user.id).pipe(
        catchError((error: unknown) => {
          this.snackbarService.open('Failed to load certificate credentials');
          console.error('Error loading certificate credentials:', error);
          return of([]);
        }),
      ),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ webauthn, certificates }) => {
          this.allCredentials = [
            ...(webauthn || []).map((c): Credential => ({ ...c, credentialType: CREDENTIAL_TYPE.WEBAUTHN })),
            ...(certificates || []).map((c): Credential => ({ ...c, credentialType: CREDENTIAL_TYPE.CERTIFICATE })),
          ];
        },
      });
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
        takeUntil(this.destroy$),
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
