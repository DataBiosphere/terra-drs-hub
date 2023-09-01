# DrsHub (Also known as Dr. Martha Shub, MD)
## Overview
DrsHub is the [DRS](https://ga4gh.github.io/data-repository-service-schemas/preview/develop/docs/) resolution service for Terra. It is the hub through which DRS requests are routed, therefore: DRSHub.

It is a Java Spring Boot rewrite of the deprecated Cloud Function [Martha](https://github.com/broadinstitute/martha), specifically, its v3 API.

### Background Info

- [The adoption of DRS across data repositories](https://docs.google.com/document/d/1Wf4enSGOEXD5_AE-uzLoYqjIp5MnePbZ6kYTVFp1WoM/edit#heading=h.qiwlmit3m9)
- [Global Alliance for Genomics and Health (GA4GH) DRS specifications](https://ga4gh.github.io/data-repository-service-schemas/preview/develop/docs/)

## DRS Providers

This is the short name, full name, and auth type(s) for each provider

- **AnVIL** (NHGRI Analysis Visualization and Informatics Lab-space)
  - Fence Token
- **BDC** (BioData Catalyst)
  - Fence Token
- **CRDC** (NCI Cancer Research/Proteomics Data Commons)
  - Fence Token
- **KidsFirst** (Gabriella Miller Kids First DRC)
  - Fence Token
- **Passport Test** (Passport Test Provider)
  - Passport
  - Fence Token
- **TDR** (Terra Data Repo)
  - Bearer Token

## Usage
To resolve a DRS URL, perform an HTTP `POST` to `/api/v4/drs/resolve`.
The content-type of your request should be `application/json` with the content/body of your request encoded accordingly.

Request bodies should look like
```json
{
  "url": "string",
  "fields": ["string"]
}
```
where `url` is the DRS URL to resolve and `fields` is any of
```text
accessUrl
bondProvider
bucket
contentType
fileName
googleServiceAccount
gsUri
hashes
localizationPath
name
size
timeCreated
timeUpdated
```

If no `fields` are specified in the request, the response will include the following `fields` by default:
```text
bucket
contentType
fileName
gsUri
hashes
localizationPath
name
size
timeCreated
timeUpdated
googleServiceAccount
```

## Architecture
DrsHub is a Java 17 Spring Boot application running in Kubernetes. As it simply resolves urls and doesn't have any state, it has no database. For developer convenience, a Swagger UI is provided.

Some architecture diagrams can be found in [LucidChart](https://lucid.app/documents#/documents?folder_id=297026717)

## Development

### Setup
Install Java 17 SDK from your preferred provider. A common way to install and manage different JDK versions is to use [sdkman](https://sdkman.io/).

If developing in IntelliJ, you can just configure the Project SDK to use Java 17.
You'll also need to set the Gradle JVM, located at `Preferences | Build, Execution, Deployment | Build Tools | Gradle`.

You must use [git-secrets](https://github.com/awslabs/git-secrets). You should be doing this anyway for all of your repositories.
DrsHub uses [Minnie Kenny](https://minnie-kenny.readthedocs.io/en/latest/), and is configured to run `minnie_kenny.sh` on `./gradlew test` tasks, ensuring that git-secrets is set up.
You can also run it manually to make sure `git-secrets` is set up without testing.

Before running anything, make sure to run `./render_config <ENV>` to render secrets locally. The default `<ENV>` is `dev`, which is what you should use for local running and testing.

DrsHub uses Gradle as a build tool. Some common Gradle commands you may want to run are
```shell
./gradlew generateSwaggerCode # Generate Swagger code for models and Swagger UI
./gradlew bootRun # Run DrsHub locally (Swagger UI at localhost:8080)
./gradlew unitTest # Run the unit tests
./gradlew jib # Build the DrsHub Docker image
```

### Run Integration Tests
DrsHub uses [TestRunner](https://github.com/DataBiosphere/terra-test-runner) to run its integration tests.
To run the integration test suite, run
```shell
./gradlew runTest --args="suites/FullIntegration.json /tmp/test-results"
```
Adding `--stacktrace` can give you more debugging information, if needed.

### Run Pact Tests
To run the Pact tests, run the following:

```shell
export PACT_BROKER_URL="pact-broker.dsp-eng-tools.broadinstitute.org"
export PACT_PROVIDER_COMMIT="$(git rev-parse HEAD)"
export PACT_PROVIDER_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
export PACT_BROKER_USERNAME="$(vault read -field=basic_auth_read_only_username secret/dsp/pact-broker/users/read-only)"
export PACT_BROKER_PASSWORD="$(vault read -field=basic_auth_read_only_password secret/dsp/pact-broker/users/read-only)"

./gradlew verifyPacts
```

### Logging
By default, DrsHub will emit logs in the Stackdriver JSON format.
To disable this behavior for local development, add `DRSHUB_LOG_APPENDER=Console-Standard` to your environment when running DrsHub.

## Deployment
DrsHub runs in Kubernetes in GCP. Current deployments for each env can be found at:
- Dev
  - [Kubernetes Deployment](https://console.cloud.google.com/kubernetes/deployment/us-central1-a/terra-dev/terra-dev/drshub-deployment/overview?project=broad-dsde-dev)
  - [Swagger UI](https://drshub.dsde-dev.broadinstitute.org/)
- Alpha
  - [Kubernetes Deployment](https://console.cloud.google.com/kubernetes/deployment/us-central1-a/terra-alpha/terra-alpha/drshub-deployment/overview?project=broad-dsde-alpha)
  - [Swagger UI](https://drshub.dsde-alpha.broadinstitute.org/)
- Perf
  - [Kubernetes Deployment](https://console.cloud.google.com/kubernetes/deployment/us-central1-a/terra-perf/terra-perf/drshub-deployment/overview?project=broad-dsde-perf)
  - [Swagger UI](https://drshub.dsde-perf.broadinstitute.org/)
- Staging
  - [Kubernetes Deployment](https://console.cloud.google.com/kubernetes/deployment/us-central1-a/terra-staging/terra-staging/drshub-deployment/overview?project=broad-dsde-staging)
  - [Swagger UI](https://drshub.dsde-staging.broadinstitute.org/)

### DRS Provider Compact ID/URIs per Environment

Note: there are a few tricky cases with the **compact IDs (CID)**:
- BioDataCatalyst uses the CIB `dg.4503` in production, and `dg.712c` in non-prod environments
- The AnVIL currently has two CIBs in use
  - `dg.anv0` for old gen3 hosted data
  - `drs.anv0` (note the **dg** vs **drs** prefix) for TDR hosted data, this will be used going forward
- Most of these providers have only `prod` and `not prod` URIs, TDR is the only one that has specific URIs for each lower environment

### Dev
| Provider            | Compact Id (CIB)  | Host URI                                   |
|---------------------|-------------------|--------------------------------------------|
| AnVIL (gen3 hosted) | dg.anv0           | jade.datarepo-dev.broadinstitute.org       |
| AnVIL (TDR hosted)  | drs.anv0          | jade.datarepo-dev.broadinstitute.org       |
| BDC                 | dg.712c           | staging.gen3.biodatacatalyst.nhlbi.nih.gov |
| CRDC                | dg.4dfc           | nci-crdc-staging.datacommons.io            |
| KidsFirst           | dg.f82a1a         | gen3staging.kidsfirstdrc.org               |
| Passport Test       | dg.test0          | ctds-test-env.planx-pla.net                |

### Alpha
| Provider            | Compact Id (CIB)  | Host URI                                   |
|---------------------|-------------------|--------------------------------------------|
| AnVIL (gen3 hosted) | dg.anv0           | data.alpha.envs-terra.bio                  |
| AnVIL (TDR hosted)  | drs.anv0          | data.alpha.envs-terra.bio                  |
| BDC                 | dg.712c           | staging.gen3.biodatacatalyst.nhlbi.nih.gov |
| CRDC                | dg.4dfc           | nci-crdc-staging.datacommons.io            |
| KidsFirst           | dg.f82a1a         | gen3staging.kidsfirstdrc.org               |
| Passport Test       | dg.test0          | ctds-test-env.planx-pla.net                |

### Staging

| Provider            | Compact Id (CIB)  | Host URI                                   |
|---------------------|-------------------|--------------------------------------------|
| AnVIL (gen3 hosted) | dg.anv0           | data.staging.envs-terra.bio                |
| AnVIL (TDR hosted)  | drs.anv0          | data.staging.envs-terra.bio                |
| BDC                 | dg.712c           | staging.gen3.biodatacatalyst.nhlbi.nih.gov |
| CRDC                | dg.4dfc           | nci-crdc-staging.datacommons.io            |
| KidsFirst           | dg.f82a1a         | gen3staging.kidsfirstdrc.org               |
| Passport Test       | dg.test0          | ctds-test-env.planx-pla.net                |

### Prod

| Provider            | Compact Id (CIB)  | Host URI                           |
|---------------------|-------------------|------------------------------------|
| AnVIL (gen3 hosted) | dg.anv0           | data.terra.bio                     |
| AnVIL (TDR hosted)  | drs.anv0          | data.terra.bio                     |
| BDC                 | dg.4503           | gen3.biodatacatalyst.nhlbi.nih.gov |
| CRDC                | dg.4dfc           | nci-crdc.datacommons.io            |
| KidsFirst           | dg.f82a1a         | data.kidsfirstdrc.org              |
| Passport Test       |                   |                                    |

## SonarCloud Status
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=DataBiosphere_terra-drs-hub&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=DataBiosphere_terra-drs-hub)

