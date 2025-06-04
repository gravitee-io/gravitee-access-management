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
import { filter, switchMap, tap } from 'rxjs/operators';

import { EntrypointService } from '../../../services/entrypoint.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-entrypoints',
  templateUrl: './entrypoints.component.html',
  styleUrls: ['./entrypoints.component.scss'],
  standalone: false,
})
export class EntrypointsComponent implements OnInit {
  public entrypoints: any[];

  constructor(
    private entrypointService: EntrypointService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.entrypoints = this.route.snapshot.data['entrypoints'];
  }

  get isEmpty() {
    return !this.entrypoints || this.entrypoints.length === 0;
  }

  loadEntrypoints() {
    this.entrypointService.list().subscribe((response) => (this.entrypoints = response));
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Entrypoint', 'Are you sure you want to delete this entrypoint ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.entrypointService.delete(id)),
        tap(() => {
          this.snackbarService.open('Entrypoint deleted');
          this.loadEntrypoints();
        }),
      )
      .subscribe();
  }

  canDelete(entrypoint) {
    return !entrypoint.defaultEntrypoint && this.authService.hasPermissions(['organization_entrypoint_delete']);
  }
}
