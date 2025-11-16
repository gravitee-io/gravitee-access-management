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
/* eslint-disable no-console */
export const ansi = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  gray: '\x1b[90m',
};

export const ICON = {
  ok: '✔',
  fail: '✖',
  warn: '⚠',
  info: 'ℹ',
  rocket: '🚀',
  broom: '🧹',
  gear: '⚙',
  sparkles: '✨',
  hourglass: '⌛',
};

/**
 * Render a header-style banner with a title for major phases.
 * @param title Title to display inside the banner
 */
export function banner(title: string) {
  const line = '─'.repeat(Math.max(10, title.length + 4));
  console.log(`${ansi.cyan}${'┌' + line + '┐'}${ansi.reset}`);
  console.log(`${ansi.cyan}│${ansi.reset}  ${ansi.bold}${title}${ansi.reset}  ${ansi.cyan}│${ansi.reset}`);
  console.log(`${ansi.cyan}${'└' + line + '┘'}${ansi.reset}`);
}

/**
 * Start a new logical section in the CLI output.
 * @param label Section label
 */
export function section(label: string) {
  console.log(`\n${ansi.magenta}${ICON.gear} ${label}${ansi.reset}`);
}

/**
 * Print an informational message line.
 * @param msg Message to display
 */
export function info(msg: string) {
  console.log(`${ansi.blue}${ICON.info} ${msg}${ansi.reset}`);
}

/**
 * Print a success message line (green check).
 * @param msg Message to display
 */
export function success(msg: string) {
  console.log(`${ansi.green}${ICON.ok} ${msg}${ansi.reset}`);
}

/**
 * Print a warning message line (yellow).
 * @param msg Message to display
 */
export function warn(msg: string) {
  console.log(`${ansi.yellow}${ICON.warn} ${msg}${ansi.reset}`);
}

/**
 * Print an error message line (red).
 * @param msg Message to display
 */
export function errorLog(msg: string) {
  console.log(`${ansi.red}${ICON.fail} ${msg}${ansi.reset}`);
}

/**
 * Print a small bullet line (gray), typically for details under a section.
 * @param msg Message to display
 */
export function bullet(msg: string) {
  console.log(`${ansi.gray}  • ${msg}${ansi.reset}`);
}

// Spinners
const frames = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];
type Spinner = { timer?: NodeJS.Timeout; i: number; text: string; renderLen?: number };
let activeSpinner: Spinner | null = null;

function stripAnsi(s: string): string {
  return s.replace(/\x1B\[[0-9;]*m/g, '');
}

/**
 * Start a single-line animated spinner with text.
 * Caller should later call stopSpinner to clear the line.
 * @param text Initial spinner text
 */
export function startSpinner(text: string): Spinner {
  if (activeSpinner) {
    stopSpinner(activeSpinner);
  }
  const s: Spinner = { i: 0, text, renderLen: 0 };
  s.timer = setInterval(() => {
    const frame = frames[(s.i = (s.i + 1) % frames.length)];
    const line = `${ansi.cyan}${frame} ${ansi.reset}${s.text}${ansi.reset}   `;
    process.stdout.write(`\r${line}`);
    s.renderLen = stripAnsi(line).length;
  }, 80);
  activeSpinner = s;
  return s;
}

export function updateSpinner(s: Spinner, text: string) {
  s.text = text;
}

/**
 * Stop an active spinner, clear its line, and optionally print a final line.
 * @param s Spinner handle returned by startSpinner
 * @param finalText Optional final message to render after clearing
 */
export function stopSpinner(s: Spinner, finalText?: string) {
  if (s.timer) clearInterval(s.timer);
  const len = s.renderLen || 0;
  if (len > 0) {
    process.stdout.write('\r' + ' '.repeat(len) + '\r');
  } else {
    process.stdout.write('\r');
  }
  if (finalText) {
    console.log(finalText);
  }
  if (activeSpinner === s) {
    activeSpinner = null;
  }
}
