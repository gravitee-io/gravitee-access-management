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
import {AuthService} from '../../../services/auth.service';
import {FormControl} from "@angular/forms";
import {Observable} from 'rxjs';
import {map, startWith} from "rxjs/operators";

@Component({
  selector: 'app-general',
  templateUrl: './entrypoints.component.html',
  styleUrls: ['./entrypoints.component.scss']
})
export class DomainSettingsEntrypointsComponent implements OnInit {
  @ViewChild('chipInput') chipInput: MatInput;
  formChanged = false;
  domain: any = {};
  entrypoint: any;
  readonly = false;
  switchModeLabel: string;
  hostControl: FormControl = new FormControl();
  domainRestrictions: string[];
  domainRegexList: RegExp[] = [];
  hostOptions: Observable<string[]>;
  hostPattern: string;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService) {
  }

  ngOnInit() {

    this.domain = this.route.snapshot.data['domain'];
    this.entrypoint = this.route.snapshot.data['entrypoint'];

    if (this.domain.vhosts === undefined) {
      this.domain.vhosts = [];
    }

    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
    this.changeSwitchModeLabel();

    this.domainRestrictions = this.route.snapshot.data['environment'].domainRestrictions;

    if (this.domainRestrictions === undefined) {
      this.domainRestrictions = [];
    }

    if (this.domainRestrictions.length === 0) {
      this.hostPattern = '^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$';
    } else {
      this.hostPattern = '^' + this.domainRestrictions.map(value => '((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)?' + value).join('$|') + '$';
    }

    // Prepare host regex (used to assist user when specifying an host).
    this.domainRestrictions.forEach(hostOption => this.domainRegexList.push(new RegExp('\\.?' + hostOption, 'i')));
  }

  update() {
    this.domainService.patchEntrypoints(this.domain.id, this.domain).subscribe(response => {
      this.domain = response;
      this.domainService.notify(this.domain);
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
      this.domain.vhosts.push({host: '', path: this.domain.path, overrideEntrypoint: true});
    } else {
      this.domain.vhosts.push({host: '', path: '/'});
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

  getHostOptions(host: string): string[] {
    this.domainRegexList.forEach(regex => host = host.replace(regex, ''));

    if (host !== '' && !this.domainRestrictions.includes(host)) {
      return this.domainRestrictions.map(domain => host + '.' + domain);
    }

    return this.domainRestrictions;
  }

  focus(input: HTMLInputElement) {
    if (this.domainRestrictions.includes(input.value) && !input.value.startsWith('.')) {
      input.value = '.' + input.value
    }

    for (let i = 0; i < this.domainRegexList.length; i++) {
      let match = input.value.match(this.domainRegexList[i]);

      if (match) {
        let index = input.value.indexOf(match[0]);
        input.setSelectionRange(index, index, 'none');
        break;
      }
    }
  }

  unfocus(vhost : any, input: HTMLInputElement) {
    if(input.value.startsWith('.')) {
      input.value = input.value.replace('.', '');
    }

    if(vhost.host.startsWith('.')) {
      vhost.host = vhost.host.replace('.', '');
    }
  }

  hostSelected(input: HTMLInputElement) {
    input.blur();
    this.formChanged = true;
  }
}
