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
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatInput } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';
import { difference, find, map, remove } from 'lodash';

import { DomainService } from '../../../services/domain.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { NavbarService } from '../../../components/navbar/navbar.service';

export interface Tag {
  id: string;
  name: string;
}

@Component({
  selector: 'app-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss'],
})
export class DomainSettingsGeneralComponent implements OnInit {
  @ViewChild('chipInput', { static: true }) chipInput: MatInput;
  @ViewChild('deleteDomainBtn', { static: false }) deleteDomainBtn: any;
  private envId: string;
  formChanged = false;
  domain: any = {};
  dataPlanes: any[] = [];
  domainOIDCSettings: any = {};
  tags: Tag[];
  selectedTags: Tag[];
  readonly = false;
  logoutRedirectUri: string;
  logoutRedirectUris: any[] = [];
  requestUri: string;
  requestUris: any[] = [];

  constructor(
    private domainService: DomainService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private navbarService: NavbarService,
  ) {}

  ngOnInit() {
    this.envId = this.route.snapshot.params['envHrid'];
    this.domain = this.route.snapshot.data['domain'];
    this.dataPlanes = this.route.snapshot.data['dataPlanes'];
    this.domainOIDCSettings = this.domain.oidc || {};
    this.logoutRedirectUris = map(this.domainOIDCSettings.postLogoutRedirectUris, function (item) {
      return { value: item };
    });
    this.requestUris = map(this.domainOIDCSettings.requestUris, function (item) {
      return { value: item };
    });
    if (this.domain.tags === undefined) {
      this.domain.tags = [];
    }
    this.initTags();
    this.updateDataPlaneName();
  }

  private updateDataPlaneName() {
    if (this.domain.dataPlaneId && this.dataPlanes) {
      this.domain.dataPlaneName = this.dataPlanes.find((dp) => dp.id === this.domain.dataPlaneId)?.name;
    }
  }

  initTags() {
    const tags = this.route.snapshot.data['tags'];
    this.selectedTags = this.domain.tags.map((t) => find(tags, { id: t })).filter((t) => typeof t !== 'undefined');
    this.tags = difference(tags, this.selectedTags);
  }

  addTag(event) {
    this.selectedTags = this.selectedTags.concat(remove(this.tags, { id: event.option.value }));
    this.tagsChanged();
  }

  removeTag(tag) {
    this.selectedTags = this.selectedTags.filter((t) => t.id !== tag.id);
    this.tags.push(tag);
    this.tagsChanged();
  }

  tagsChanged() {
    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
    this.domain.tags = map(this.selectedTags, (tag) => tag.id);
  }

  enableDomain(event) {
    this.domain.enabled = event.checked;
    this.formChanged = true;
  }

  setMaster(event) {
    this.domain.master = event.checked;
    this.formChanged = true;
  }

  addLogoutRedirectUris(event) {
    event.preventDefault();
    if (this.logoutRedirectUri) {
      const sanitizedUri = this.logoutRedirectUri.trim();
      if (!this.logoutRedirectUris.some((el) => el.value === sanitizedUri)) {
        this.logoutRedirectUris.push({ value: sanitizedUri });
        this.logoutRedirectUris = [...this.logoutRedirectUris];
        this.logoutRedirectUri = null;
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : redirect URI "${sanitizedUri}" already exists`);
      }
    }
  }

  addRequestUris(event) {
    event.preventDefault();
    if (this.requestUri) {
      const sanitizedUri = this.requestUri.trim();
      if (!this.requestUris.some((el) => el.value === sanitizedUri)) {
        this.requestUris.push({ value: sanitizedUri });
        this.requestUris = [...this.requestUris];
        this.requestUri = null;
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : request URI "${sanitizedUri}" already exists`);
      }
    }
  }

  deleteLogoutRedirectUris(logoutRedirectUri, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove logout redirect URI', 'Are you sure you want to remove this redirect URI ?')
      .pipe(
        filter((res) => res),
        tap(() => {
          remove(this.logoutRedirectUris, { value: logoutRedirectUri });
          this.logoutRedirectUris = [...this.logoutRedirectUris];
          this.formChanged = true;
        }),
      )
      .subscribe();
  }

  deleteRequestUris(requestUri, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove request URI', 'Are you sure you want to remove this request URI ?')
      .pipe(
        filter((res) => res),
        tap(() => {
          remove(this.requestUris, { value: requestUri });
          this.requestUris = [...this.requestUris];
          this.formChanged = true;
        }),
      )
      .subscribe();
  }

  update() {
    this.domain.oidc = {
      postLogoutRedirectUris: map(this.logoutRedirectUris, 'value'),
      requestUris: map(this.requestUris, 'value'),
    };
    this.domainService.patchGeneralSettings(this.domain.id, this.domain).subscribe((response) => {
      this.domainService.notify(response);
      this.formChanged = false;
      this.snackbarService.open('Domain ' + this.domain.name + ' updated');
      // if hrid has changed, reload the page
      if (response.id !== this.domain.id) {
        this.router.navigate(['/environments', this.envId, 'domains', response.id, 'settings', 'general']);
      } else {
        this.domain = response;
      }
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Domain', 'Are you sure you want to delete this domain ?')
      .pipe(
        filter((res) => res),
        tap(() => {
          this.deleteDomainBtn.nativeElement.loading = true;
          this.deleteDomainBtn.nativeElement.disabled = true;
        }),
        switchMap(() => this.domainService.delete(this.domain.id)),
        tap(() => {
          this.deleteDomainBtn.nativeElement.loading = false;
          this.snackbarService.open('Domain ' + this.domain.name + ' deleted');
          this.navbarService.notifyDomain({});
          this.router.navigate(['']);
        }),
      )
      .subscribe();
  }
}
