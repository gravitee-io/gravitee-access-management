import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseHotfix extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'release-hotfix');
    this.command
            .description('trigger pipeline release hotfix on CircleCI to create docker image from specific branch')
            .usage('npm run am-release -- --branch="4.3.x" --gio-version="4.3.2-hotfix.1" --dry-run release-hotfix');
  }


  computePipelineParameters(options) {
    return {
      tag_latest: options.tagLatest,
      tag_latest_support: options.tagLatestSupport,
    };
  }
  computePipelineParameters(options) {
    return {
      dry_run: options.dryRun,
      graviteeio_version: options.gioVersion,
      gio_action: 'release-hotfix-version',
      hotfix_version: true,
      tag_latest: false,
      tag_latest_support: false,
      create_maintenance_version_branch: false
    };
  }
}