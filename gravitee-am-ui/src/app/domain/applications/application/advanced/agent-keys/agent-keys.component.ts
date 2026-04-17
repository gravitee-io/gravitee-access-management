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
import { filter, switchMap } from 'rxjs/operators';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { AuthService } from '../../../../../services/auth.service';
import { ApplicationAgentKeysService, AgentJwk } from '../../../../../services/application-agent-keys.service';

import { AgentKeyAddDialogComponent } from './add-dialog/agent-key-add-dialog.component';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { error?: { message?: string } } | undefined;
  return maybe?.error?.message ?? fallback;
}

@Component({
  selector: 'app-application-agent-keys',
  templateUrl: './agent-keys.component.html',
  styleUrls: ['./agent-keys.component.scss'],
  standalone: false,
})
export class ApplicationAgentKeysComponent implements OnInit {
  readonly displayedColumns = ['kid', 'kty', 'alg', 'use', 'actions'];
  application: any;
  domainId: string;
  keys: AgentJwk[] = [];
  editMode = false;

  constructor(
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private dialog: MatDialog,
    private authService: AuthService,
    private agentKeysService: ApplicationAgentKeysService,
  ) {}

  ngOnInit(): void {
    this.application = this.route.snapshot.data['application'];
    this.domainId = this.route.snapshot.data['domain']?.id ?? this.application.domain;
    this.editMode = this.authService.hasPermissions(['application_openid_update']);
    this.reload();
  }

  reload(): void {
    this.agentKeysService.list(this.domainId, this.application.id).subscribe({
      next: (keys) => {
        this.keys = keys ?? [];
      },
    });
  }

  openAddDialog(): void {
    this.dialog
      .open(AgentKeyAddDialogComponent, { width: '640px', disableClose: true, autoFocus: false })
      .afterClosed()
      .pipe(
        filter((jwk): jwk is AgentJwk => !!jwk),
        switchMap((jwk) => this.agentKeysService.add(this.domainId, this.application.id, jwk)),
      )
      .subscribe({
        next: () => {
          this.snackbarService.open('Agent key added');
          this.reload();
        },
        error: (err: unknown) => {
          this.snackbarService.open(errorMessage(err, 'Failed to add agent key'));
        },
      });
  }

  remove(kid: string): void {
    this.dialogService
      .confirm('Remove agent key', `Remove key '${kid}'? The agent will no longer be able to authenticate with this key.`)
      .pipe(
        filter((ok) => !!ok),
        switchMap(() => this.agentKeysService.remove(this.domainId, this.application.id, kid)),
      )
      .subscribe({
        next: () => {
          this.snackbarService.open('Agent key removed');
          this.reload();
        },
        error: (err: unknown) => {
          this.snackbarService.open(errorMessage(err, 'Failed to remove agent key'));
        },
      });
  }
}
