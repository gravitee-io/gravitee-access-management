export interface BulkDeleteUsersResponse {
  results: {
    index: number;
    httpStatus: number;
    success: boolean;
    errorDetails?: string;
    response?: string;
  }[];
}

export function BulkDeleteUsersResponseFromJSON(json: any): BulkDeleteUsersResponse {
  return BulkDeleteUsersResponseFromJSONTyped(json, false);
}

export function BulkDeleteUsersResponseFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkDeleteUsersResponse {
  if (json === undefined || json === null) {
    return json;
  }
  return json as BulkDeleteUsersResponse;
}
