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
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, filter } from 'rxjs/operators';
import { of } from 'rxjs';

import {
  ProtectedResource,
  ProtectedResourceFeature,
  ProtectedResourceService,
  ProtectedResourceFeatureType,
  UpdateProtectedResourceRequest,
} from '../../../../services/protected-resource.service';
import { McpTool } from '../../../components/mcp-tools-table/mcp-tools-table.component';
import { ScopeService } from '../../../../services/scope.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { AuthService } from '../../../../services/auth.service';
import { DialogService } from '../../../../services/dialog.service';
import { DomainNewMcpServerToolDialogFactory } from '../../mcp-server-new/tool-new-dialog/tool-new-dialog.component';

import { DomainMcpServerToolEditDialogComponent } from './tool-edit-dialog/tool-edit-dialog.component';

/**
 * Extended type for ProtectedResourceFeature that includes scopes.
 * The API returns features with scopes for MCP tools, but the base type doesn't include it.
 */
interface ProtectedResourceFeatureWithScopes extends ProtectedResourceFeature {
  scopes?: string[];
}

@Component({
  selector: 'app-domain-mcp-server-tools',
  templateUrl: './tools.component.html',
  styleUrls: ['./tools.component.scss'],
  standalone: false,
})
export class DomainMcpServerToolsComponent implements OnInit {
  domainId: string;
  protectedResource: ProtectedResource;
  features: McpTool[];
  canUpdate: boolean = false;
  domainScopes: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private protectedResourceService: ProtectedResourceService,
    private scopeService: ScopeService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private dialog: MatDialog,
    private dialogService: DialogService,
    private newToolDialogFactory: DomainNewMcpServerToolDialogFactory,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.protectedResource = this.route.snapshot.data['mcpServer'];
    this.features = this.mapFeaturesToTools(this.protectedResource.features ?? []);
    this.canUpdate = this.authService.hasPermissions(['protected_resource_update']);

    // Load domain scopes for the edit dialog
    this.loadDomainScopes();

    // Reload the protected resource from API to get latest data
    this.reloadProtectedResource();
  }

  private loadDomainScopes(): void {
    this.scopeService.findByDomain(this.domainId, 0, 100).subscribe((response) => {
      this.domainScopes = response.data;
    });
  }

  private reloadProtectedResource(): void {
    this.protectedResourceService.findById(this.domainId, this.protectedResource.id, this.protectedResource.type).subscribe((resource) => {
      this.protectedResource = resource;
      this.features = this.mapFeaturesToTools(resource.features ?? []);
    });
  }

  handleAdd(): void {
    this.newToolDialogFactory.openDialog({ scopes: this.domainScopes }, (result) => {
      if (!result.cancel) {
        this.addTool(result);
      }
    });
  }

  handleEdit(tool: McpTool): void {
    const dialogRef = this.dialog.open(DomainMcpServerToolEditDialogComponent, {
      width: '540px',
      data: {
        tool: tool,
        scopes: this.domainScopes,
      },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.updateTool(tool.key, result);
      }
    });
  }

  handleDelete(tool: McpTool): void {
    this.dialogService
      .confirm('Delete Tool', `Are you sure you want to delete the tool "${tool.key}"?`)
      .pipe(filter((confirmed) => confirmed))
      .subscribe(() => {
        this.deleteTool(tool.key);
      });
  }

  private addTool(newTool: { name?: string; description?: string; scopes?: string[] }): void {
    // Trim the tool name and description
    const trimmedName = newTool.name?.trim();
    const trimmedDescription = newTool.description?.trim();

    // Check if tool with same name already exists
    if (this.protectedResource.features.some((f) => f.key === trimmedName)) {
      this.snackbarService.open(`Tool with name "${trimmedName}" already exists`);
      return;
    }

    // Add the new tool to the features list
    const updatedFeatures = [
      ...this.protectedResource.features.map((f) => ({
        key: f.key,
        description: f.description,
        type: 'MCP_TOOL' as ProtectedResourceFeatureType,
        scopes: (f as any).scopes,
      })),
      {
        key: trimmedName,
        description: trimmedDescription,
        type: 'MCP_TOOL' as ProtectedResourceFeatureType,
        scopes: newTool.scopes,
      },
    ];

    const updateRequest: UpdateProtectedResourceRequest = {
      name: this.protectedResource.name,
      resourceIdentifiers: this.protectedResource.resourceIdentifiers,
      description: this.protectedResource.description,
      features: updatedFeatures,
    };

    this.protectedResourceService
      .update(this.domainId, this.protectedResource.id, updateRequest)
      .pipe(
        catchError((err: unknown) => {
          this.snackbarService.open(
            'Failed to add tool: ' + ((err as HttpErrorResponse).error?.message || (err as HttpErrorResponse).message),
          );
          return of(null);
        }),
      )
      .subscribe((updated) => {
        if (updated) {
          this.snackbarService.open(`Tool "${newTool.name}" added successfully`);
          this.protectedResource = updated;
          this.features = this.mapFeaturesToTools(updated.features ?? []);
          // Update the route snapshot data so other tabs see the updated data
          this.route.snapshot.data['mcpServer'] = updated;
        }
      });
  }

  private deleteTool(toolKey: string): void {
    // Remove the tool from the features list
    const updatedFeatures = this.protectedResource.features.filter((feature) => feature.key !== toolKey);

    // Map features to the update request format
    const updateRequest: UpdateProtectedResourceRequest = {
      name: this.protectedResource.name,
      resourceIdentifiers: this.protectedResource.resourceIdentifiers,
      description: this.protectedResource.description,
      features: updatedFeatures.map((f) => ({
        key: f.key,
        description: f.description,
        type: 'MCP_TOOL' as ProtectedResourceFeatureType,
        scopes: (f as any).scopes,
      })),
    };

    this.protectedResourceService
      .update(this.domainId, this.protectedResource.id, updateRequest)
      .pipe(
        catchError((err: unknown) => {
          this.snackbarService.open(
            'Failed to delete tool: ' + ((err as HttpErrorResponse).error?.message || (err as HttpErrorResponse).message),
          );
          return of(null);
        }),
      )
      .subscribe((updated) => {
        if (updated) {
          this.snackbarService.open(`Tool "${toolKey}" deleted successfully`);
          this.protectedResource = updated;
          this.features = this.mapFeaturesToTools(updated.features ?? []);
          // Update the route snapshot data so other tabs see the updated data
          this.route.snapshot.data['mcpServer'] = updated;
        }
      });
  }

  private updateTool(originalKey: string, updatedTool: { key: string; description?: string; scopes?: string[] }): void {
    // Trim the tool key and description
    const trimmedKey = updatedTool.key?.trim();
    const trimmedDescription = updatedTool.description?.trim();

    // Update the tool in the features list
    const updatedFeatures = this.protectedResource.features.map((feature) => {
      if (feature.key === originalKey) {
        return {
          key: trimmedKey,
          description: trimmedDescription,
          type: 'MCP_TOOL' as ProtectedResourceFeatureType,
          scopes: updatedTool.scopes,
        };
      }
      return {
        key: feature.key,
        description: feature.description,
        type: 'MCP_TOOL' as ProtectedResourceFeatureType,
        scopes: (feature as any).scopes,
      };
    });

    const updateRequest: UpdateProtectedResourceRequest = {
      name: this.protectedResource.name,
      resourceIdentifiers: this.protectedResource.resourceIdentifiers,
      description: this.protectedResource.description,
      features: updatedFeatures,
    };

    this.protectedResourceService
      .update(this.domainId, this.protectedResource.id, updateRequest)
      .pipe(
        catchError((err: unknown) => {
          this.snackbarService.open(
            'Failed to update tool: ' + (err as HttpErrorResponse).error?.message || (err as HttpErrorResponse).message,
          );
          return of(null);
        }),
      )
      .subscribe((updated) => {
        if (updated) {
          this.snackbarService.open('Tool updated successfully');
          this.protectedResource = updated;
          this.features = this.mapFeaturesToTools(updated.features ?? []);
          // Update the route snapshot data so other tabs see the updated data
          this.route.snapshot.data['mcpServer'] = updated;
        }
      });
  }

  private mapFeaturesToTools(features: ProtectedResourceFeature[]): McpTool[] {
    return features.map((feature) => {
      const featureWithScopes = feature as ProtectedResourceFeatureWithScopes;
      return {
        key: feature.key,
        description: feature.description,
        scopes: featureWithScopes.scopes ?? [],
      };
    });
  }
}
