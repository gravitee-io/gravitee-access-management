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
import { MatDialog } from '@angular/material/dialog';
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { catchError, filter, switchMap, tap } from 'rxjs/operators';
import { EMPTY } from 'rxjs';

import { SnackbarService } from '../../../../services/snackbar.service';
import { McpServersService } from '../../mcp-servers.service';
import { AuthService } from '../../../../services/auth.service';
import { DomainStoreService } from '../../../../stores/domain.store';
import { ProtectedResource, PatchProtectedResourceRequest } from '../../../../services/protected-resource.service';
import { McpServerClientSecretService } from '../../../../services/client-secret.service';
import { ClientSecretsSettingsComponent } from '../../../../components/client-secrets-management/dialog/client-secrets-settings/client-secrets-settings.component';

@Component({
  selector: 'app-domain-mcp-server-client-secrets',
  templateUrl: './domain-mcp-server-client-secrets.component.html',
  standalone: false,
})
export class DomainMcpServerClientSecretsComponent implements OnInit {
  domainId: string;
  domain: any;
  protectedResource: ProtectedResource;
  editMode: boolean;
  deleteMode: boolean;

  constructor(
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private mcpServersService: McpServersService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
    public mcpServerClientSecretService: McpServerClientSecretService,
    private matDialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domain = this.domainStore.current;
    this.domainId = this.domain.id;
    this.protectedResource = this.route.snapshot.data['mcpServer'];

    this.editMode = this.authService.hasPermissions(['protected_resource_update']);
    this.deleteMode = this.authService.hasPermissions(['protected_resource_delete']);
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
          secretSettings: this.protectedResource.secretSettings,
        },
      })
      .afterClosed()
      .pipe(
        filter((result) => result !== undefined),
        switchMap((result) => {
          const patchData: PatchProtectedResourceRequest = {
            secretSettings: result,
          };
          return this.mcpServersService.patch(this.domainId, this.protectedResource.id, patchData).pipe(
            tap((resource) => {
              this.protectedResource.secretSettings = resource.secretSettings;
              this.snackbarService.open('Secret settings updated');
            }),
            catchError(() => {
              this.snackbarService.open('Failed to update secret settings');
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe();
  }

  getDomainSettingsUrl() {
    const domainId = this.domainId;
    const environment = this.domain.referenceId;
    return `/environments/${environment}/domains/${domainId}/settings/secrets`.toLowerCase();
  }
}
