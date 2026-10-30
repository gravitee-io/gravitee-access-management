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
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import moment from 'moment';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthService } from '../../../../services/auth.service';
import { AuthorizationDetailTypeService } from '../../../../services/authorization-detail-type.service';
import { DialogService } from '../../../../services/dialog.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import {
  DESCRIPTION_MAX_LENGTH,
  expiresInSeconds,
  expiryError,
  formatSchema,
  NAME_MAX_LENGTH,
  NAME_PATTERN,
  saveErrorMessage,
  SCHEMA_EDITOR_OPTIONS,
  schemaError,
} from '../authorization-detail-type.validation';
import { AuthorizationDetailTypeSchemaInfoDialogComponent } from '../dialog/authorization-detail-type-schema-info.component';

@Component({
  selector: 'app-authorization-detail-type',
  templateUrl: './authorization-detail-type.component.html',
  styleUrls: ['../authorization-detail-type.form.scss'],
  standalone: false,
})
export class AuthorizationDetailTypeComponent implements OnInit {
  private domainId: string;
  private original: any;
  private expiryCleared = false;
  private failedSave: { message: string; changes: string };
  readonly nameMaxLength = NAME_MAX_LENGTH;
  readonly namePattern = NAME_PATTERN;
  readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;
  authorizationDetailType: any;
  schemaEditorOptions: any;
  expiresIn: number;
  unitTime: moment.unitOfTime.DurationConstructor;
  editMode: boolean;
  deleteMode: boolean;

  constructor(
    private authorizationDetailTypeService: AuthorizationDetailTypeService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
    private dialogService: DialogService,
    private authService: AuthService,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.editMode = this.authService.hasPermissions(['domain_authorization_detail_type_update']);
    this.deleteMode = this.authService.hasPermissions(['domain_authorization_detail_type_delete']);
    this.schemaEditorOptions = { ...SCHEMA_EDITOR_OPTIONS, readOnly: !this.editMode };
    this.setCurrent(this.route.snapshot.data['authorizationDetailType']);
  }

  get schemaError() {
    return schemaError(this.authorizationDetailType.schema);
  }

  get expiryError() {
    return expiryError(this.expiresIn, this.unitTime);
  }

  get hasChanges() {
    return Object.keys(this.changes()).length > 0;
  }

  get saveError() {
    return this.failedSave?.changes === JSON.stringify(this.changes()) ? this.failedSave.message : null;
  }

  get expiry() {
    return moment.duration(this.authorizationDetailType.expiresIn, 'seconds').humanize();
  }

  clearExpiry() {
    this.authorizationDetailType.expiresIn = null;
    this.expiryCleared = true;
  }

  openSchemaExample() {
    const { type, schema } = this.authorizationDetailType;
    this.dialog
      .open(AuthorizationDetailTypeSchemaInfoDialogComponent, { data: { type, hasSchema: !!schema?.trim(), editable: this.editMode } })
      .afterClosed()
      .subscribe((example) => {
        if (example) {
          this.authorizationDetailType.schema = example;
        }
      });
  }

  formatSchema() {
    this.authorizationDetailType.schema = formatSchema(this.authorizationDetailType.schema);
  }

  enableDiscovery(event) {
    this.authorizationDetailType.discovery = event.checked;
  }

  update() {
    const changes = this.changes();
    this.authorizationDetailTypeService.patch(this.domainId, this.original.id, changes).subscribe({
      next: (data) => {
        this.setCurrent(data);
        this.snackbarService.open('Authorization detail type updated');
      },
      error: (error: unknown) => (this.failedSave = { message: saveErrorMessage(error), changes: JSON.stringify(changes) }),
    });
  }

  delete(event: Event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Authorization Detail Type', 'Are you sure you want to delete this authorization detail type?')
      .pipe(
        filter((confirmed) => confirmed),
        switchMap(() => this.authorizationDetailTypeService.delete(this.domainId, this.original.id)),
        tap(() => {
          this.snackbarService.open('Authorization detail type deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  private setCurrent(authorizationDetailType: any) {
    this.original = authorizationDetailType;
    this.authorizationDetailType = { ...authorizationDetailType };
    this.expiresIn = null;
    this.unitTime = null;
    this.expiryCleared = false;
    this.failedSave = null;
  }

  private changes() {
    const current = {
      name: this.authorizationDetailType.name,
      description: this.authorizationDetailType.description || null,
      schema: this.authorizationDetailType.schema?.trim() ? this.authorizationDetailType.schema : null,
      discovery: this.authorizationDetailType.discovery,
      expiresIn: expiresInSeconds(this.expiresIn, this.unitTime) ?? this.authorizationDetailType.expiresIn ?? null,
    };
    return Object.keys(current)
      .filter((field) => current[field] !== (this.original[field] ?? null) || (field === 'expiresIn' && this.expiryCleared))
      .reduce((changes, field) => ({ ...changes, [field]: current[field] }), {});
  }
}
