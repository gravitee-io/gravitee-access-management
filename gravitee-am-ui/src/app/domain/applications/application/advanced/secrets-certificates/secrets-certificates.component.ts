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
import { deepClone } from '@gravitee/ui-components/src/lib/utils';
import { MatDialog } from '@angular/material/dialog';
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { catchError, filter, switchMap, tap } from 'rxjs/operators';
import { EMPTY } from 'rxjs';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { DomainStoreService } from '../../../../../stores/domain.store';
import { AuthService } from '../../../../../services/auth.service';
import { ClientSecretsSettingsComponent } from '../../../../../components/client-secrets-management/dialog/client-secrets-settings/client-secrets-settings.component';
import { ApplicationClientSecretService } from '../../../../../services/client-secret.service';

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
  secretSettings: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private domainStore: DomainStoreService,
    private authService: AuthService,
    public applicationClientSecretService: ApplicationClientSecretService,
    private matDialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.domain = deepClone(this.domainStore.current);
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.certificates = this.route.snapshot.data['certificates'];
    this.secretSettings = this.application.settings.secretExpirationSettings;
    this.editMode = this.authService.hasPermissions(['application_openid_update']);
  }

  onCertificateSave(certificateId: string): void {
    const data = { certificate: certificateId ? certificateId : null };
    this.applicationService.patch(this.domain.id, this.application.id, data).subscribe((data) => {
      this.application = data;
      this.route.snapshot.data['application'] = this.application;
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
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
