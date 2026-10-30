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

import { AuthorizationDetailTypeService } from '../../../../services/authorization-detail-type.service';
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
  TYPE_MAX_LENGTH,
  TYPE_PATTERN,
} from '../authorization-detail-type.validation';
import { AuthorizationDetailTypeSchemaInfoDialogComponent } from '../dialog/authorization-detail-type-schema-info.component';

@Component({
  selector: 'app-authorization-detail-type-creation',
  templateUrl: './authorization-detail-type-creation.component.html',
  styleUrls: ['../authorization-detail-type.form.scss'],
  standalone: false,
})
export class AuthorizationDetailTypeCreationComponent implements OnInit {
  private domainId: string;
  private failedSave: { message: string; payload: string };
  readonly typeMaxLength = TYPE_MAX_LENGTH;
  readonly typePattern = TYPE_PATTERN;
  readonly nameMaxLength = NAME_MAX_LENGTH;
  readonly namePattern = NAME_PATTERN;
  readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;
  readonly schemaEditorOptions = SCHEMA_EDITOR_OPTIONS;
  authorizationDetailType: any = { discovery: false };
  expiresIn: number;
  unitTime: moment.unitOfTime.DurationConstructor;

  constructor(
    private authorizationDetailTypeService: AuthorizationDetailTypeService,
    private router: Router,
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  get schemaError() {
    return schemaError(this.authorizationDetailType.schema);
  }

  get expiryError() {
    return expiryError(this.expiresIn, this.unitTime);
  }

  get saveError() {
    return this.failedSave?.payload === JSON.stringify(this.payload()) ? this.failedSave.message : null;
  }

  openSchemaExample() {
    const { type, schema } = this.authorizationDetailType;
    this.dialog
      .open(AuthorizationDetailTypeSchemaInfoDialogComponent, { data: { type, hasSchema: !!schema?.trim(), editable: true } })
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

  create() {
    const payload = this.payload();
    this.authorizationDetailTypeService.create(this.domainId, payload).subscribe({
      next: (data) => {
        this.snackbarService.open('Authorization detail type ' + data.type + ' created');
        this.router.navigate(['..', data.id], { relativeTo: this.route });
      },
      error: (error: unknown) => (this.failedSave = { message: saveErrorMessage(error), payload: JSON.stringify(payload) }),
    });
  }

  private payload() {
    const { type, name, description, schema, discovery } = this.authorizationDetailType;
    return {
      type,
      name,
      discovery,
      description: description || undefined,
      schema: schema?.trim() ? schema : undefined,
      expiresIn: expiresInSeconds(this.expiresIn, this.unitTime),
    };
  }
}
