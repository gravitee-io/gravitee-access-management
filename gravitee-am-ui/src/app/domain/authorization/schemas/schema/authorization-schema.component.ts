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
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthorizationSchemaService } from '../../../../services/authorization-schema.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-authorization-schema',
  templateUrl: './authorization-schema.component.html',
  styleUrls: ['./authorization-schema.component.scss'],
  standalone: false,
})
export class AuthorizationSchemaDetailComponent implements OnInit {
  schema: any;
  domainId: string;
  formChanged = false;
  editMode: boolean;

  content = '';
  commitMessage = '';

  versions: any[] = [];
  selectedVersion: any = null;

  constructor(
    private authorizationSchemaService: AuthorizationSchemaService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.schema = this.route.snapshot.data['schema'];
    this.editMode = this.authService.hasPermissions(['domain_authorization_bundle_update']);
    this.loadLatestVersion();
  }

  loadLatestVersion() {
    this.authorizationSchemaService.getVersion(this.domainId, this.schema.id, this.schema.latestVersion).subscribe((v) => {
      this.content = v.content || '';
    });
  }

  loadVersions() {
    this.authorizationSchemaService.getVersions(this.domainId, this.schema.id).subscribe((versions) => {
      this.versions = versions.sort((a: any, b: any) => b.version - a.version);
    });
  }

  update() {
    const updatePayload = {
      name: this.schema.name,
      content: this.content,
      commitMessage: this.commitMessage,
    };
    this.authorizationSchemaService.update(this.domainId, this.schema.id, updatePayload).subscribe((data) => {
      this.schema = data;
      this.formChanged = false;
      this.commitMessage = '';
      this.loadLatestVersion();
      this.loadVersions();
      this.snackbarService.open('Authorization schema updated');
    });
  }

  delete(event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Authorization Schema', 'Are you sure you want to delete this authorization schema?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationSchemaService.delete(this.domainId, this.schema.id)),
        tap(() => {
          this.snackbarService.open('Authorization schema deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  viewVersion(version: any) {
    this.selectedVersion = version;
  }

  closeVersionView() {
    this.selectedVersion = null;
  }

  restoreVersion(version: any) {
    this.dialogService
      .confirm('Restore Version', `Restore to version ${version.version}? This will create a new version with that content.`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationSchemaService.restoreVersion(this.domainId, this.schema.id, version.version)),
        tap((data) => {
          this.schema = data;
          this.loadLatestVersion();
          this.loadVersions();
          this.selectedVersion = null;
          this.snackbarService.open(`Restored to version ${version.version}`);
        }),
      )
      .subscribe();
  }

  onTabChange(index: number) {
    if (index === 1) {
      this.loadVersions();
    }
  }
}
