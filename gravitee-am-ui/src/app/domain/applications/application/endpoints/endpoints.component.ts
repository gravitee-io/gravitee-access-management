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
import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {AuthService} from "../../../../services/auth.service";
import {SnackbarService} from "../../../../services/snackbar.service";
import {EntrypointService} from "../../../../services/entrypoint.service";

@Component({
  selector: 'application-overview',
  templateUrl: './endpoints.component.html',
  styleUrls: ['./endpoints.component.scss']
})
export class ApplicationEndpointsComponent implements OnInit {
  application: any;
  entrypoint: any;
  private baseUrl: string;
  @ViewChild('copyText', { read: ElementRef }) copyText: ElementRef;

  constructor(private route: ActivatedRoute,
              private snackbarService: SnackbarService,
              private entrypointService: EntrypointService) {
  }

  ngOnInit() {
    const domain = this.route.snapshot.data['domain'];
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.application = this.route.snapshot.parent.data['application'];
    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, domain);
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  endpoint(path) {
    return this.baseUrl + path;
  }

}
