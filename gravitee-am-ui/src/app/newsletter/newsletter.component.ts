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
import {Component, HostListener, OnInit, ViewChild} from '@angular/core';
import '@gravitee/ui-components/wc/gv-newsletter-subscription';
import {Router} from "@angular/router";
import {AuthService} from "../services/auth.service";
import {SnackbarService} from "../services/snackbar.service";

@Component({
  selector: 'app-newsletter',
  templateUrl: './newsletter.component.html',
  styleUrls: ['./newsletter.component.scss']
})
export class NewsletterComponent implements OnInit {

  @ViewChild('newsletter', {static: true}) newsletter;
  email: string = '';

  constructor(private router: Router,
              private snackbarService: SnackbarService,
              private authService: AuthService) { }

  ngOnInit() {
    this.authService.userInfo().subscribe(user => {
        this.email = user.email || '';
    });
  }

  @HostListener(':gv-newsletter-subscription:subscribe', ['$event.detail'])
  onSubscribe(detail) {
    this.authService.subscribeNewsletter(detail).subscribe(__ => {
      this.snackbarService.open('Your newsletter preference has been saved.');
      this.router.navigate(['/']);
    })
  }

  @HostListener(':gv-newsletter-subscription:skip')
  onSkip() {
    this.router.navigate(['/']);
  }
}
