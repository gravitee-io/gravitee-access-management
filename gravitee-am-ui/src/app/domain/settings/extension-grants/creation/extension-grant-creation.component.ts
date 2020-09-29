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
import {MatStepper} from '@angular/material';
import {ActivatedRoute, Router} from '@angular/router';
import {ExtensionGrantService} from '../../../../services/extension-grant.service';
import {SnackbarService} from '../../../../services/snackbar.service';

@Component({
  selector: 'app-extension-grant-creation',
  templateUrl: './extension-grant-creation.component.html',
  styleUrls: ['./extension-grant-creation.component.scss']
})
export class ExtensionGrantCreationComponent implements OnInit {
  private domainId: string;
  extensionGrant: any = {};
  configurationIsValid = false;
  @ViewChild ('stepper') stepper: MatStepper;

  constructor(private extensionGrantService: ExtensionGrantService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
  }

  create() {
    this.extensionGrant.configuration = JSON.stringify(this.extensionGrant.configuration);
    this.extensionGrantService.create(this.domainId, this.extensionGrant).subscribe(data => {
      this.snackbarService.open('Extension grant ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.extensionGrant &&
      this.extensionGrant.name &&
      this.extensionGrant.grantType &&
      this.configurationIsValid;
  }

}
