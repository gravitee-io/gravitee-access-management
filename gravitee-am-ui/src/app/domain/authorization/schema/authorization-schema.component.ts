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

import { AuthorizationSchemaService } from '../../../services/authorization-schema.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-authorization-schema',
  templateUrl: './authorization-schema.component.html',
  styleUrls: ['./authorization-schema.component.scss'],
  standalone: false,
})
export class AuthorizationSchemaComponent implements OnInit {
  schema: any;
  versions: any[] = [];
  domainId: string;
  formChanged = false;
  editMode: boolean;
  updateComment = '';

  constructor(
    private authorizationSchemaService: AuthorizationSchemaService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.schema = this.route.snapshot.data['authorizationSchema'];
    this.editMode = this.authService.hasPermissions(['domain_authorization_schema_update']);
    if (this.schema) {
      this.loadVersions();
    } else {
      this.schema = { engineType: 'cedar', content: '' };
    }
  }

  get isNew() {
    return !this.schema?.id;
  }

  loadVersions() {
    if (!this.schema?.id) return;
    this.authorizationSchemaService.getVersions(this.domainId).subscribe(
      (versions) => (this.versions = versions),
      () => {},
    );
  }

  save() {
    const payload = {
      engineType: this.schema.engineType,
      content: this.schema.content,
      comment: this.updateComment || undefined,
    };
    this.authorizationSchemaService.update(this.domainId, payload).subscribe((result) => {
      this.schema = result;
      this.formChanged = false;
      this.updateComment = '';
      this.snackbarService.open(this.isNew ? 'Authorization schema created' : 'Authorization schema updated');
      this.loadVersions();
    });
  }

  rollback(version: number) {
    this.dialogService
      .confirm('Rollback Schema', 'Are you sure you want to rollback to version ' + version + '?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationSchemaService.rollback(this.domainId, version)),
        tap((result) => {
          this.schema = result;
          this.snackbarService.open('Schema rolled back to version ' + version);
          this.loadVersions();
        }),
      )
      .subscribe();
  }
}
