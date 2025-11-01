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
import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { McpServersService } from '../../../../mcp-servers/mcp-servers.service';
import { DialogService } from '../../../../../services/dialog.service';
import { AuthService } from '../../../../../services/auth.service';
import { DomainStoreService } from '../../../../../stores/domain.store';
import { ProtectedResource } from '../../../../../services/protected-resource.service';

@Component({
  selector: 'app-domain-mcp-server-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss'],
  standalone: false,
})
export class DomainMcpServerGeneralComponent implements OnInit, OnDestroy {
  @ViewChild('settingsComponent', { static: false }) settingsComponent: any;

  domainId: string;
  domain: any;
  protectedResource: ProtectedResource;
  resourceUri: string;
  initialValues: { name: string; description: string; resourceUri: string };
  formChanged = false;
  editMode: boolean;
  deleteMode: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private mcpServersService: McpServersService,
    private authService: AuthService,
    private dialogService: DialogService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.domainId = this.domain.id;
    this.protectedResource = structuredClone(this.route.snapshot.data['mcpServer']);
    this.resourceUri = this.protectedResource.resourceIdentifiers?.[0] || '';

    // Store initial values for change detection
    this.initialValues = {
      name: this.protectedResource.name || '',
      description: this.protectedResource.description || '',
      resourceUri: this.resourceUri || '',
    };

    this.editMode = this.authService.hasPermissions(['protected_resource_update']);
    this.deleteMode = this.authService.hasPermissions(['protected_resource_delete']);

    // Check if form has changes
    this.checkFormChanges();
  }

  ngOnDestroy() {
    // Cleanup if needed
  }

  onSettingsChange(settings: { name: string; resourceIdentifier: string; description: string }): void {
    this.protectedResource.name = settings.name;
    this.protectedResource.description = settings.description;
    this.resourceUri = settings.resourceIdentifier;
    this.checkFormChanges();
  }

  update() {
    const patchData: any = {
      name: this.protectedResource.name,
      description: this.protectedResource.description,
      resourceIdentifiers: [this.resourceUri?.trim()?.toLowerCase() || ''],
    };

    this.mcpServersService.patch(this.domainId, this.protectedResource.id, patchData).subscribe(
      () => {
        // Update initial values after successful save
        this.initialValues = {
          name: this.protectedResource.name || '',
          description: this.protectedResource.description || '',
          resourceUri: this.resourceUri || '',
        };
        this.formChanged = false;
        this.snackbarService.open('MCP Server updated');
        this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      },
      (error) => {
        if (error.error?.message) {
          this.snackbarService.open(error.error.message);
        } else {
          this.snackbarService.open('Failed to update MCP Server');
        }
      },
    );
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete MCP Authorization Server', 'Are you sure you want to delete this MCP Authorization Server?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.mcpServersService.delete(this.domainId, this.protectedResource.id)),
        tap(() => {
          this.snackbarService.open('MCP Authorization Server deleted');
          this.router.navigate(['/environments', this.domain.referenceId, 'domains', this.domain.id, 'mcp-servers']);
        }),
      )
      .subscribe();
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  onFormChange() {
    this.checkFormChanges();
  }

  private checkFormChanges() {
    const currentName = (this.protectedResource.name || '').trim();
    const currentDescription = (this.protectedResource.description || '').trim();
    const currentResourceUri = (this.resourceUri || '').trim().toLowerCase();

    const initialName = (this.initialValues.name || '').trim();
    const initialDescription = (this.initialValues.description || '').trim();
    const initialResourceUri = (this.initialValues.resourceUri || '').trim().toLowerCase();

    this.formChanged =
      currentName !== initialName ||
      currentDescription !== initialDescription ||
      currentResourceUri !== initialResourceUri;
  }

  hasFormChanges(): boolean {
    return this.formChanged && !this.settingsComponent?.nameDuplicateError;
  }
}
