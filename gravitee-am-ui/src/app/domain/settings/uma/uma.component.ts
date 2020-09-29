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
import {SnackbarService} from "../../../services/snackbar.service";
import {ActivatedRoute, Router} from "@angular/router";
import {DomainService} from "../../../services/domain.service";
import {AuthService} from "../../../services/auth.service";

@Component({
  selector: 'app-uma',
  templateUrl: './uma.component.html',
  styleUrls: ['./uma.component.scss']
})
export class UmaComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              private router: Router) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.domain = this.route.snapshot.data['domain'];
    this.editMode = this.authService.hasPermissions(['domain_uma_update']);
  }

  save() {
    this.domainService.patchUmaSettings(this.domainId, this.domain).subscribe(data => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('UMA configuration updated');
    });
  }

  enableUMA(event) {
    this.domain.uma = { 'enabled': (event.checked) };
    this.formChanged = true;
  }

  isUMAEnabled() {
    return this.domain.uma && this.domain.uma.enabled;
  }
}
