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
import {Component, OnInit, ViewChild} from '@angular/core';
import {MatInput} from '@angular/material';
import {ActivatedRoute, Router} from '@angular/router';
import {DomainService} from '../../../services/domain.service';
import {DialogService} from '../../../services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {BreadcrumbService} from '../../../services/breadcrumb.service';
import {AuthService} from '../../../services/auth.service';

@Component({
  selector: 'app-general',
  templateUrl: './entrypoints.component.html',
  styleUrls: ['./entrypoints.component.scss']
})
export class DomainSettingsEntrypointsComponent implements OnInit {
  @ViewChild('chipInput', { static: false }) chipInput: MatInput;
  formChanged = false;
  domain: any = {};
  entrypoint: any;
  readonly = false;
  switchModeLabel: string;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private breadcrumbService: BreadcrumbService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.parent.data['domain'];
    this.entrypoint = this.route.snapshot.data['entrypoint'];

    if (this.domain.vhosts === undefined) {
      this.domain.vhosts = [];
    }

    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
    this.changeSwitchModeLabel();
  }

  update() {
    this.domainService.patchEntrypoints(this.domain.id, this.domain).subscribe(response => {
      this.domain = response;
      this.domainService.notify(this.domain);
      this.breadcrumbService.addFriendlyNameForRoute('/domains/' + this.domain.id, this.domain.name);
      this.formChanged = false;
      this.snackbarService.open('Domain ' + this.domain.name + ' updated');
    });
  }

  switchMode() {
    this.domain.vhostMode = !this.domain.vhostMode
    this.changeSwitchModeLabel();
    this.formChanged = true;
  }

  addVhost() {
    if (this.domain.vhosts.length === 0) {
      this.domain.vhosts.push({ host: '', path: this.domain.path, overrideEntrypoint: true });
    } else {
      this.domain.vhosts.push({ host: '', path: '/' });
    }
  }


  remove(vhost: any) {
    this.domain.vhosts = this.domain.vhosts.filter(v => v !== vhost);
    this.formChanged = true;
  }

  changeSwitchModeLabel() {
    if (this.domain.vhostMode === true) {
      this.switchModeLabel = 'context-path';
    } else {
      this.switchModeLabel = 'virtual hosts';
    }
  }

  overrideEntrypointChange(vhost: any) {
    this.domain.vhosts.filter(v => v !== vhost).forEach(v => v.overrideEntrypoint = false);
    vhost.overrideEntrypoint = true;
    this.formChanged = true;
  }
}
