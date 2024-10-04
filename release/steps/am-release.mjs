#!/usr/bin/env zx
import { Command } from 'commander';
import { checkCircleCIToken } from '../helpers/circleci-helper.mjs';
import logger from '../helpers/logger.mjs';
import { ReleaseGit } from './1-step-release-git.mjs';
import { ReleaseMavenCentral } from './2-step-release-maven-central.mjs';
import { ReleaseDocker } from './3-step-release-docker.mjs';
import { ReleaseRPM } from './4-step-release-rpm.mjs';
import { ReleaseHelm } from './5-step-release-helm.mjs';
import { ReleaseNotes } from './6-step-release-notes.mjs';
import { ReleaseVersion } from "./am-release-pipeline.mjs";
import { ReleaseAlphaVersion } from "./am-release-alpha-pipeline.mjs";
import { ReleaseHotfix } from './am-release-hotfix.mjs';

function changeLogLevel(newLevel) {
  logger.level = newLevel;
}

function setupCommanderJS() {
  const program = new Command();

  program
    .name('am-release')
    .description('Trigger CircleCI action to run release steps')
    .usage(': npm run am-release -- [--global-option] <command> [--command-option]')
    .version('0.0.1')
    .enablePositionalOptions()
    .option('-o, --org', 'Organisation name (default: gravitee-io)', 'gravitee-io')
    .option('-r, --repo', 'Repository name (default: gravitee-access-management)', 'gravitee-access-management')
    .option('--dry-run', 'run in dry-run mode', false)
    .option('--log-level <info|warn|debug>', 'setup the log level (default: info)', changeLogLevel, 'info')
    .requiredOption('-b, --branch <string>', 'branch name where CI job should run')
    .requiredOption('-giov, --gio-version <string>', 'GIO release version');

  program.showHelpAfterError('(add --help for additional information)');

  return program;
}

async function main() {
  // overwrite zx logger
  $.log = (entry) => {
    logger.debug(entry);
  };

  // As mentioned here: https://github.com/tj/commander.js/issues/1592
  // commander.js expect to receive first two args: node binary path and script name
  // we should remove zs from the args
  let args = process.argv.filter((arg, index) => {
    let rejectedArgument = index < 3 && arg.endsWith('zx');
    return !rejectedArgument;
  });

  const program = setupCommanderJS();
  new ReleaseGit().buildCommand(program);
  new ReleaseMavenCentral().buildCommand(program);
  new ReleaseDocker().buildCommand(program);
  new ReleaseRPM().buildCommand(program);
  new ReleaseHelm().buildCommand(program);
  new ReleaseNotes().buildCommand(program);
  new ReleaseVersion().buildCommand(program);
  new ReleaseAlphaVersion().buildCommand(program);
  new ReleaseHotfix().buildCommand(program);

  await checkCircleCIToken();
  // This parse call will trigger action needed.
  program.parse(args);
}

await main();