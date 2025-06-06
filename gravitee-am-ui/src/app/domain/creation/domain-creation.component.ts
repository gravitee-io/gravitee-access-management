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
import { ActivatedRoute, Router } from '@angular/router';
import { get } from 'lodash';

import { DomainService } from '../../services/domain.service';
import { SnackbarService } from '../../services/snackbar.service';

@Component({
  selector: 'app-creation',
  templateUrl: './domain-creation.component.html',
  styleUrls: ['./domain-creation.component.scss'],
  standalone: false,
})
export class DomainCreationComponent implements OnInit {
  domain: any = {};
  dataPlanes: any[];
  displayNavLink: boolean;
  oneDataPlane = false;
  @ViewChild('createDomainBtn', { static: true }) createDomainBtn: any;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.displayNavLink = !this.router.url.startsWith('/settings');
    this.dataPlanes = this.route.snapshot.data['dataPlanes'];
    if (this.dataPlanes.length === 1) {
      this.domain.dataPlaneId = this.dataPlanes[0].id;
      this.oneDataPlane = true;
    } else {
      this.dataPlanes.sort((a, b) => a.name.localeCompare(b.name));
    }
  }

  create() {
    this.createDomainBtn.nativeElement.loading = true;
    this.createDomainBtn.nativeElement.disabled = true;
    this.domainService.create(this.domain).subscribe(
      (data) => {
        this.createDomainBtn.nativeElement.loading = false;
        this.snackbarService.open('Domain ' + data.name + ' created');
        this.router.navigate(['..', data.id], { relativeTo: this.route });
      },
      (error: unknown) => {
        this.createDomainBtn.nativeElement.loading = false;
        this.snackbarService.openFromComponent('Errors', [get(error, 'error.message')]);
      },
    );
  }
}
