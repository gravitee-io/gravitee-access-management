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
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { EMPTY, Observable } from 'rxjs';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { CertificateService } from '../../../../../services/certificate.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

import { NewClientSecretComponent } from './new-client-secret/new-client-secret.component';
import { CopyClientSecretComponent, CopyClientSecretCopyDialogData } from './copy-client-secret/copy-client-secret.component';
import { RenewClientSecretComponent } from './renew-client-secret/renew-client-secret.component';
import { ClientSecretsSettingsComponent } from './client-secrets-settings/client-secrets-settings.component';
import { DeleteClientSecretComponent, DeleteClientSecretData } from './delete-client-secret/delete-client-secret.component';

export interface ClientSecret {
  id: string;
  name: string;
  status: string;
  createdAt: string;
  expiryDate: string;
}

@Component({
  selector: 'app-application-certificates',
  templateUrl: './secrets-certificates.component.html',
  styleUrls: ['./secrets-certificates.component.scss'],
  standalone: false,
})
export class ApplicationSecretsCertificatesComponent implements OnInit {
  domain: any;
  formChanged = false;
  application: any;
  certificates: any[] = [];
  certificatePublicKeys: any[] = [];
  selectedCertificate: string;
  clientSecrets: ClientSecret[] = [];
  clientId: any;
  secretSettings: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private certificateService: CertificateService,
    private matDialog: MatDialog,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit(): void {
    this.domain = deepClone(this.domainStore.current);
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.certificates = this.route.snapshot.data['certificates'];
    if (this.application.certificate) {
      this.selectedCertificate = this.application.certificate;
      this.publicKeys(this.application.certificate);
    }
    this.clientSecrets = this.application.secrets.map((cs) => this.mapClientSecret(cs));
    this.secretSettings = this.application.settings.secretExpirationSettings;
  }

  patch(): void {
    const data = { certificate: this.selectedCertificate ? this.selectedCertificate : null };
    this.applicationService.patch(this.domain.id, this.application.id, data).subscribe((data) => {
      this.application = data;
      this.route.snapshot.data['application'] = this.application;
      this.certificatePublicKeys = [];
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      if (this.application.certificate) {
        this.publicKeys(this.application.certificate);
      }
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }

  onChange() {
    this.formChanged = true;
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  private publicKeys(certificateId) {
    this.certificateService.publicKeys(this.domain.id, certificateId).subscribe((response) => {
      this.certificatePublicKeys = response;
    });
  }

  openNewSecret(event: Event): void {
    event.preventDefault();

    this.matDialog
      .open<NewClientSecretComponent, void, string>(NewClientSecretComponent, {
        width: GIO_DIALOG_WIDTH.MEDIUM,
        disableClose: true,
        role: 'alertdialog',
        id: 'newClientSecretDialog',
      })
      .afterClosed()
      .pipe(
        filter((description) => !!description),
        switchMap((description) =>
          this.applicationService.createClientSecret(this.domain.id, this.application.id, description).pipe(
            tap(() => {
              this.snackbarService.open(`Client secret created - ${description}`);
            }),
            map((secretResponse) => ({
              secret: secretResponse.secret,
              renew: false,
            })),
            catchError((e: unknown): Observable<never> => {
              if (typeof e === 'object' && e !== null) {
                const httpStatus = (e as any).error.http_status;
                const message = (e as any).error.message || 'An error occurred';
                this.snackbarService.open(httpStatus < 500 ? message : 'Failed to create client secret');
              } else {
                this.snackbarService.open('Unknown error occurred');
              }
              return EMPTY;
            }),
          ),
        ),
        switchMap((dialogData) =>
          this.matDialog
            .open<CopyClientSecretComponent, CopyClientSecretCopyDialogData, void>(CopyClientSecretComponent, {
              width: GIO_DIALOG_WIDTH.MEDIUM,
              disableClose: true,
              data: dialogData,
              role: 'alertdialog',
              id: 'copyClientSecretDialog',
            })
            .afterClosed()
            .pipe(
              switchMap(() =>
                this.applicationService.getClientSecrets(this.domain.id, this.application.id).pipe(
                  catchError(() => {
                    this.snackbarService.open('Failed to fetch client secrets');
                    return EMPTY;
                  }),
                ),
              ),
            ),
        ),
      )
      .subscribe({
        next: (clientSecrets) => {
          this.clientSecrets = clientSecrets.map((cs) => this.mapClientSecret(cs));
        },
        error: () => {
          this.snackbarService.open('Error fetching client secrets');
        },
      });
  }

  mapClientSecret(clientSecret: any): ClientSecret {
    if (clientSecret.expiresAt) {
      return {
        ...clientSecret,
        status: clientSecret.expiresAt > Date.now() ? 'Running' : 'Expired',
      } as ClientSecret;
    } else {
      return {
        ...clientSecret,
        status: 'Running',
      } as ClientSecret;
    }
  }

  deleteSecret(row: ClientSecret, event: Event): void {
    event.preventDefault();
    this.matDialog
      .open<DeleteClientSecretComponent, DeleteClientSecretData, string>(DeleteClientSecretComponent, {
        width: GIO_DIALOG_WIDTH.MEDIUM,
        disableClose: true,
        data: { description: row.name },
        role: 'alertdialog',
        id: 'renewClientSecretDialog',
      })
      .afterClosed()
      .pipe(
        filter((result) => result === 'delete'),
        switchMap(() => this.applicationService.deleteClientSecret(this.domain.id, this.application.id, row.id)),
        switchMap(() =>
          this.applicationService.getClientSecrets(this.domain.id, this.application.id).pipe(
            catchError(() => {
              this.snackbarService.open('Failed to fetch client secrets');
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe({
        next: (secrets) => {
          this.clientSecrets = secrets.map((s) => this.mapClientSecret(s));
          this.snackbarService.open(`Secret ${row.name} deleted`);
        },
        error: () => this.snackbarService.open(`Cannot delete ${row.name}.`),
      });
  }

  renewSecret(row: any, event: any) {
    event.preventDefault();
    this.matDialog
      .open<RenewClientSecretComponent, void, string>(RenewClientSecretComponent, {
        width: GIO_DIALOG_WIDTH.SMALL,
        disableClose: true,
        role: 'alertdialog',
        id: 'renewClientSecretDialog',
      })
      .afterClosed()
      .pipe(
        filter((result) => result === 'renew'),
        switchMap(() =>
          this.applicationService.renewClientSecret(this.domain.id, this.application.id, row.id).pipe(
            tap(() => {
              this.snackbarService.open(`Client secret renewed - ${row.name}`);
            }),
            map((secretResponse) => ({
              secret: secretResponse.secret,
              renew: true,
            })),
            catchError(() => {
              this.snackbarService.open('Failed to renew client secret');
              return EMPTY;
            }),
          ),
        ),
        switchMap((dialogData) =>
          this.matDialog
            .open<CopyClientSecretComponent, CopyClientSecretCopyDialogData, void>(CopyClientSecretComponent, {
              width: GIO_DIALOG_WIDTH.SMALL,
              disableClose: true,
              data: dialogData,
              role: 'alertdialog',
              id: 'copyClientSecretDialog',
            })
            .afterClosed(),
        ),
        switchMap(() =>
          this.applicationService.getClientSecrets(this.domain.id, this.application.id).pipe(
            catchError(() => {
              this.snackbarService.open('Failed to fetch client secrets');
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe({
        next: (secrets) => {
          this.clientSecrets = secrets.map((s) => this.mapClientSecret(s));
        },
        error: () => this.snackbarService.open(`Cannot renew ${row.name}.`),
      });
  }

  openSettings(event: any) {
    event.preventDefault();
    this.matDialog
      .open(ClientSecretsSettingsComponent, {
        width: GIO_DIALOG_WIDTH.MEDIUM,
        disableClose: true,
        role: 'alertdialog',
        id: 'clientSecretSettingsDialog',
        autoFocus: false,
        data: {
          domainSettingsUrl: this.getDomainSettingsUrl(),
          domainSettings: this.domain.secretExpirationSettings,
          secretSettings: this.secretSettings,
        },
      })
      .afterClosed()
      .pipe(
        filter((result) => result !== undefined),
        switchMap((result) => {
          const toPatch = {
            settings: {
              secretExpirationSettings: result,
            },
          };
          return this.applicationService.patch(this.domain.id, this.application.id, toPatch).pipe(
            tap((application) => {
              this.secretSettings = application.settings.secretExpirationSettings;
              this.snackbarService.open('Secret settings updated');
            }),
            catchError(() => {
              this.snackbarService.open('Failed to renew client secret');
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe();
  }

  getDomainSettingsUrl() {
    const domainId = this.route.snapshot.data['domain']?.id;
    const environment = this.route.snapshot.data['domain']?.referenceId;
    return `/environments/${environment}/domains/${domainId}/settings/secrets`.toLowerCase();
  }
}
