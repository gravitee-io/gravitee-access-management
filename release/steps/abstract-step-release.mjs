import logger from '../helpers/logger.mjs';
import { callCircle } from '../helpers/circleci-helper.mjs';

export class AbstractStepRelease {
  buildCommand(program, commandName) {
    this.command = program
      .command(commandName)
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run publish-maven-central')
      .action(async (options) => {
        await this.action({ ...options, ...program.opts() });
      });
  }

  computePipelineParameters(options) {
    return {}; // to override
  }

  async action(options) {
    logger.debug('options: %o', options);

    const customParameter = this.computePipelineParameters(options);

    const pipelineArgs = {
      branch: options.branch,
      parameters: {
        dry_run: options.dryRun,
        graviteeio_version: options.gioVersion,
        ...customParameter,
      },
    };

    await callCircle(options.org, options.repo, pipelineArgs);
  }
}
