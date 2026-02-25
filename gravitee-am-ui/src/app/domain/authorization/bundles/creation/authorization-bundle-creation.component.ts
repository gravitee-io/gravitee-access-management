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

import { AuthorizationBundleService } from '../../../../services/authorization-bundle.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-authorization-bundle-creation',
  templateUrl: './authorization-bundle-creation.component.html',
  styleUrls: ['./authorization-bundle-creation.component.scss'],
  standalone: false,
})
export class AuthorizationBundleCreationComponent implements OnInit {
  bundle: any = { engineType: 'cedar' };
  domainId: string;

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    this.authorizationBundleService.create(this.domainId, this.bundle).subscribe(() => {
      this.snackbarService.open('Authorization bundle created');
      this.router.navigate(['..'], { relativeTo: this.route });
    });
  }
}
