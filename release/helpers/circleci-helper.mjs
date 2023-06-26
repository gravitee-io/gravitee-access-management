import logger from './logger.mjs';

require('dotenv').config();

/**
 * Check if user has a valid CircleCI Token. If no token, asks him to enter one.
 * @return {Promise<void>}
 */
export async function checkCircleCIToken() {
  if (!process.env.CIRCLECI_TOKEN) {
    logger.error('CIRCLECI_TOKEN environment variable is not set');
    logger.error('Please setup your token before calling this script:');
    logger.error('By doing: `export CIRCLECI_TOKEN=<your token>`');
    logger.error('Or create an `.env` file and add the variable CIRCLECI_TOKEN');
    process.exit();
  }

  let response = await fetch('https://circleci.com/api/v2/me', {
    headers: {
      'Circle-Token': process.env.CIRCLECI_TOKEN,
    },
  });

  let body = await response.json();
  if (response.status === 401) {
    logger.error('Unauthorized CircleCI token');
    process.exit();
  } else {
    logger.info(`Logged as ${body.login}\n`);
  }
}
/**
 * Check if user has a valid CircleCI Token. If no token, asks him to enter one.
 * @return {Promise<void>}
 */
export async function callCircle(organisation_name, repository_name, pipeline_arguments) {
  logger.debug(
    `fetch https://circleci.com/api/v2/project/gh/${organisation_name}/${repository_name}/pipeline with ${JSON.stringify(
      pipeline_arguments,
    )}`,
  );

  const response = await fetch(`https://circleci.com/api/v2/project/gh/${organisation_name}/${repository_name}/pipeline`, {
    method: 'post',
    body: JSON.stringify(pipeline_arguments),
    headers: {
      'Content-Type': 'application/json',
      'Circle-Token': process.env.CIRCLECI_TOKEN,
    },
  });

  const data = await response.json();

  if (response.status === 201) {
    logger.info(`Pipeline created with number: ${data.number}`);
    logger.info(`Follow its progress on: https://app.circleci.com/pipelines/github/${organisation_name}/${repository_name}/${data.number}`);
  } else {
    logger.error(`Something went wrong during a call to CircleCI to ${repository_name} with args: ${pipeline_arguments}`);
  }
}
