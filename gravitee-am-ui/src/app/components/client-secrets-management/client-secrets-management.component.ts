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

import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { EMPTY, Observable } from 'rxjs';

import { SnackbarService } from '../../services/snackbar.service';
import { ClientSecretService, ClientSecret } from '../../services/client-secret.service';

import { NewClientSecretComponent } from './dialog/new-client-secret/new-client-secret.component';
import { CopyClientSecretComponent, CopyClientSecretCopyDialogData } from './dialog/copy-client-secret/copy-client-secret.component';
import { RenewClientSecretComponent } from './dialog/renew-client-secret/renew-client-secret.component';
import { DeleteClientSecretComponent, DeleteClientSecretData } from './dialog/delete-client-secret/delete-client-secret.component';

@Component({
  selector: 'app-client-secrets-management',
  templateUrl: './client-secrets-management.component.html',
  styleUrls: ['./client-secrets-management.component.scss'],
  standalone: false,
})
export class ClientSecretsManagementComponent implements OnInit {
  @Input() service: ClientSecretService;
  @Input() domainId: string;
  @Input() parentId: string;
  @Input() clientId: string;
  @Input() showSettings = false;
  @Input() hasCreatePermission = false;
  @Input() hasUpdatePermission = false;
  @Input() hasDeletePermission = false;
  @Output() settingsClicked = new EventEmitter<any>();

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  // TODO: Handle Settings input if needed (e.g. initial settings)
  // For now, fetching on openSettings

  clientSecrets: ClientSecret[] = [];

  constructor(
    private snackbarService: SnackbarService,
    private matDialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.loadSecrets();
  }

  loadSecrets(): void {
    if (this.service && this.domainId && this.parentId) {
      this.service.list(this.domainId, this.parentId).subscribe({
        next: (secrets) => {
          this.clientSecrets = secrets.map((s) => this.mapClientSecret(s));
        },
        error: () => this.snackbarService.open('Error fetching client secrets'),
      });
    }
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
          this.service.create(this.domainId, this.parentId, description).pipe(
            tap(() => {
              this.snackbarService.open(`Client secret created - ${description}`);
            }),
            map((secretResponse) => ({
              secret: secretResponse.secret,
              renew: false,
            })),
            catchError((_e: unknown): Observable<never> => {
              this.snackbarService.open('Failed to create client secret');
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
            .pipe(switchMap(() => this.service.list(this.domainId, this.parentId))),
        ),
      )
      .subscribe({
        next: (clientSecrets) => {
          this.clientSecrets = clientSecrets.map((s) => this.mapClientSecret(s));
        },
        error: () => {
          this.snackbarService.open('Error fetching client secrets');
        },
      });
  }

  mapClientSecret(clientSecret: any): ClientSecret {
    // Basic mapping, extend if settings are present for expiry check
    // If showSettings is true, assume expiry fields exist
    let status = 'Running';
    // If expiresAt exists check it
    if (clientSecret.expiresAt && clientSecret.expiresAt < Date.now()) {
      status = 'Expired';
    }
    // Application returns 'secret' field sometimes?
    // ClientSecret type in service: value, name, id.
    return {
      ...clientSecret,
      status: status,
    } as any;
  }

  deleteSecret(row: ClientSecret, event: Event): void {
    event.preventDefault();
    this.matDialog
      .open<DeleteClientSecretComponent, DeleteClientSecretData, string>(DeleteClientSecretComponent, {
        width: GIO_DIALOG_WIDTH.MEDIUM,
        disableClose: true,
        data: { description: row.name },
        role: 'alertdialog',
        id: 'deleteClientSecretDialog',
      })
      .afterClosed()
      .pipe(
        filter((result) => result === 'delete'),
        switchMap(() => this.service.delete(this.domainId, this.parentId, row.id)),
        switchMap(() => this.service.list(this.domainId, this.parentId)),
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
          this.service.renew(this.domainId, this.parentId, row.id).pipe(
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
        switchMap(() => this.service.list(this.domainId, this.parentId)),
      )
      .subscribe({
        next: (secrets) => {
          this.clientSecrets = secrets.map((s) => this.mapClientSecret(s));
        },
        error: () => this.snackbarService.open(`Cannot renew ${row.name}.`),
      });
  }

  openSettings(event: any) {
    if (!this.showSettings) return;
    event.preventDefault();
    this.settingsClicked.emit(event);
  }
}
