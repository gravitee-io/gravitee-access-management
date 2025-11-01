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
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { map, switchMap, tap } from 'rxjs/operators';
import { MatDialog } from '@angular/material/dialog';

import {
  CopyClientSecretComponent,
  CopyClientSecretCopyDialogData,
} from '../../applications/application/advanced/secrets-certificates/copy-client-secret/copy-client-secret.component';
import { SnackbarService } from '../../../services/snackbar.service';
import { McpServersService, NewMcpServer } from '../mcp-servers.service';

import { DomainNewMcpServerToolDialogFactory } from './tool-new-dialog/tool-new-dialog.component';

@Component({
  selector: 'app-new-mcp-server',
  templateUrl: './new-mcp-server.component.html',
  styleUrl: './new-mcp-server.component.scss',
  standalone: false,
})
export class DomainNewMcpServerComponent implements OnInit {
  newMcpServer = {
    tools: [],
  } as NewMcpServer;

  domain: {
    id: string;
    name: string;
  };

  scopes: any[];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private readonly matDialog: MatDialog,
    private newToolDialogFactory: DomainNewMcpServerToolDialogFactory,
    private snackbarService: SnackbarService,
    private service: McpServersService,
  ) {}
  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    this.scopes = this.route.snapshot.data['scopes'];
  }

  onSettingsChange(settings: { name: string; resourceIdentifier: string; description: string }): void {
    this.newMcpServer.name = settings.name;
    this.newMcpServer.resourceIdentifier = settings.resourceIdentifier;
    this.newMcpServer.description = settings.description;
  }

  registerMcpServer(): void {
    this.service
      .create(this.domain.id, this.newMcpServer)
      .pipe(
        switchMap((data) =>
          this.matDialog
            .open<CopyClientSecretComponent, CopyClientSecretCopyDialogData, void>(CopyClientSecretComponent, {
              width: GIO_DIALOG_WIDTH.MEDIUM,
              disableClose: true,
              data: {
                secret: data.clientSecret,
                renew: true,
              },
              role: 'alertdialog',
              id: 'applicationClientSecretCopyDialog',
            })
            .afterClosed()
            .pipe(
              tap(() => {
                this.snackbarService.open('MCP Server ' + data.name + ' created');
              }),
              map(() => data),
            ),
        ),
      )
      .subscribe((data) => {
        this.router.navigate(['..', data.id], { relativeTo: this.route });
      });
  }

  openAddToolModal(event: Event): void {
    event.preventDefault();
    this.newToolDialogFactory.openDialog({ scopes: this.scopes }, (data) => {
      if (!data.cancel) {
        if (this.newMcpServer.tools.find((tool) => tool.key === data.name)) {
          this.snackbarService.open(`Tool with name ${data.name} already exists`);
        } else {
          this.newMcpServer.tools = [
            ...this.newMcpServer.tools,
            {
              key: data.name,
              scopes: data.scopes,
              description: data.description,
            },
          ];
        }
      }
    });
  }

  editTool(tool: any): void {
    this.newToolDialogFactory.openDialog(
      {
        scopes: this.scopes,
        tool: tool, // Pass the existing tool data
      },
      (data) => {
        if (!data.cancel) {
          // Check if name changed and if new name already exists
          if (tool.key !== data.name && this.newMcpServer.tools.find((t) => t.key === data.name)) {
            this.snackbarService.open(`Tool with name ${data.name} already exists`);
          } else {
            // Update the tool in the array
            this.newMcpServer.tools = this.newMcpServer.tools.map((t) =>
              t.key === tool.key
                ? {
                    key: data.name,
                    scopes: data.scopes,
                    description: data.description,
                  }
                : t,
            );
          }
        }
      },
    );
  }

  removeTool(toolKey: string): void {
    this.newMcpServer.tools = this.newMcpServer.tools.filter((tool) => tool.key !== toolKey);
  }

  formValid(): boolean {
    return !!(this.newMcpServer.name && this.newMcpServer.resourceIdentifier);
  }
}
