# Authorization Simulations

## Prerequisites

Provisioning simulations have no dependency on AM but do require
- an instance of **OpenFGA**
- an existing OpenFGA Store configured with an Authorization Model compatible with the tuples being generated

### Docker Compose Setup (recommended)

The command `npm --prefix docker/local-stack run stack:perf:openfga` can be used to spin up a local instance of OpenFGA within a container that is exposed on port 8090.
Using this approach, the instance is backed by a postgres database and has a compatible Authorization Model imported.

The database is persisted between runs by default. To clear it, you can remove the volume with `npm --prefix docker/local-stack run stack:perf:clear-volumes`.

After an OpenFGA store is successfully imported, its ID and the Authorization Model ID are output to the console. These can then be used in the OpenFGAProvision simulation.

(Note that the playground isn't available by these means but it is possible to access it using the hosted sandbox https://play.fga.dev/sandbox/?fga_api_host=127.0.0.1%3A8090&fga_api_scheme=http.)

### OpenFGAProvision

This is a simulation to generate relationship tuples for an OpenFGA instance.

The OpenFGA store is designed to be single tenancy (i.e. it might be used by one domain in AM) and it relies upon an authorization model that is designed to be capable of describing a large company organization.

Standard provisioning tests should generate:
- 1 million users with 1000 resources each
- 10 million users with 1000 resources each

Further types and relationships can be mapped against teams and team-owned resources as well as global/shared resources.

The resultant data generated in OpenFGA is intended to be reused in other performance simulations that target permission evaluation. (This aspect is not part of the provisioning simulation, but manual verification examples are documented below.)

#### Usage:

In the `gravitee-am-performance/` folder, run:

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.authorization.OpenFGAProvision -Dfga_store_id=01KFDJWH9H1CV45WPXB6P7Y0VB -Dfga_authorization_model_id=01KFDJWH9RWT20QN56N52YVDRC -Dfga_api_url=http://localhost:8090 -Dnumber_of_users=10000
```

#### Parameters
* `fga_api_url`: base URL of the OpenFGA REST API (default: http://localhost:8080)
* `fga_store_id`: OpenFGA Store identifier
* `fga_authorization_model_id`: OpenFGA authorization model identifier
* `number_of_users`: how many users the simulation will create tuples for
* `number_of_teams`: how many teams the simulation will create tuples for
* `depth_of_teams`: maximum depth of teams in tree hierarchy
* `number_of_resources_per_user`: how many resources per user the simulation will create tuples for
* `number_of_resources_per_team`: how many resources per team the simulation will create tuples for
* `number_of_shared_resources`: how many global resources the simulation will create tuples for

#### Authorization check examples

To verify allowed or denied permissions, send a POST request to the OpenFGA API's endpoint `{fga_api_url}/stores/{fga_store_id}/check`.

After running the `OpenFGAProvision` simulation with default settings, the following JSON request bodies should produce allowed responses.

```json
{
    "tuple_key": {
        "user":"user:user_56",
        "relation": "owner", // or "can_access"
        "object": "resource:user_56_resource_23"
    }
}
```

```json
{
    "tuple_key": {
        "user":"team:team_12#all_members", // or "user:user_12"
        "relation": "group_owner", // or "can_access"
        "object": "resource:team_12_resource_4"
    }
}
```

```json
{
    "tuple_key": {
        "user":"user:user_1", // user has shared_resources_local_reader role assignment
        "relation": "group_reader", // or "can_access"
        "object": "resource:shared_resource_14"
    },
    "context": {
        "user_ip": "192.168.0.10" // but not allowed with "10.0.0.10"
    }
}
```

```json
{
    "tuple_key": {
        "user":"user:user_8", // user has personal_resources_working_hours_reader role assignment
        "relation": "group_reader", // or "can_access"
        "object": "resource:user_19_resource_2"
    },
    "context": {
        "current_time": "2026-01-01T10:00:00Z" // but not allowed with "2026-01-01T23:00:00Z"
    }
}
```

Expected response:

`200 OK`

```json
{
    "allowed": true,
    "resolution": ""
}
```
