import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseNotes extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'release-notes');
    this.command
      .description('trigger release_notes_am step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run release-notes');
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'release_notes_am',
    };
  }
}
