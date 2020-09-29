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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {DialogService} from 'app/services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {ExtensionGrantService} from '../../../services/extension-grant.service';

@Component({
  selector: 'app-extension-grants',
  templateUrl: './extension-grants.component.html',
  styleUrls: ['./extension-grants.component.scss']
})
export class DomainSettingsExtensionGrantsComponent implements OnInit {
  private extensionGrantTypes: any = {
    'jwtbearer-am-extension-grant' : 'Extension Grant JWT Bearer'
  };
  extensionGrants: any[];
  domainId: string;

  constructor(private extensionGrantService: ExtensionGrantService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.extensionGrants = this.route.snapshot.data['extensionGrants'];
  }

  get isEmpty() {
    return !this.extensionGrants || this.extensionGrants.length === 0;
  }

  loadTokenGranters() {
    this.extensionGrantService.findByDomain(this.domainId).subscribe(response => this.extensionGrants = response);
  }

  displayType(type) {
    if (this.extensionGrantTypes[type]) {
      return this.extensionGrantTypes[type];
    }
    return type;
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Extension Grant', 'Are you sure you want to delete this extension grant ?')
      .subscribe(res => {
        if (res) {
          this.extensionGrantService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open('Extension Grant deleted');
            this.loadTokenGranters();
          });
        }
      });
  }
}
