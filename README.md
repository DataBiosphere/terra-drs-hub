# Dr. Shub, MD (Also known as DrsHub)

## Overview
DrsHub is a Java Spring Boot rewrite of the Cloud Function [Martha](https://github.com/broadinstitute/martha), specifically, its v3 API.
The purpose of DrsHub is to resolve [DRS](https://ga4gh.github.io/data-repository-service-schemas/preview/develop/docs/) URLs for Terra services.

## DRS Providers
Currently, DrsHub supports the following DRS Providers:

- Terra Data Repo (TDR)
  - Authentication Types:
    - Bearer Token
- NHGRI Analysis Visualization and Informatics Lab-space (The AnVIL)
  - Authentication Types:
    - Fence Token
- BioData Catalyst (BDC)
  - Authentication Types:
    - Fence Token
- NCI Cancer Research / Proteomics Data Commons (CRDC / PDC)
  - Authentication Types:
    - Fence Token
- Gabriella Miller Kids First DRC
  - Authentication Types:
    - Fence Token
- Passport Test Provider
  - Authentication Types:
    - Passports
    - Fence Tokens

## Usage
To resolve a DRS URL, perform an HTTP `POST` to the `/api/v4/drs/resolve`.
The content-type of your request should be application/json with the content/body of your request encoded accordingly.

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

By default, the response will include 
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
but this can be changed by providing values in the `fields` array.

## Architecture
DrsHub is a Java 17 Spring Boot application running in Kubernetes. As it simply resolves urls and doesn't have any state, it has no database. For developer convenience, a Swagger UI is provided.

Some architecture diagrams can be found in [LucidChart](https://lucid.app/documents#/documents?folder_id=297026717)

## Development

### Setup
Install Java 17 SDK from your preferred provider. A common way to install and manage different JDK versions is to use [sdkman](https://sdkman.io/). If developing in IntelliJ, you can just configure the Project SDK to use Java 17.

You must use [git-secrets](https://github.com/awslabs/git-secrets). You should be doing this anyway for all of your repositories.
DrsHub uses [Minnie Kenny](https://minnie-kenny.readthedocs.io/en/latest/), and is configured to run `minnie_kenny.sh` on `./gradlew test` tasks, ensuring that git-secrets is set up.
You can also run it manually to make sure `git-secrets` is set up without testing.

Before running anything, make sure to run `./render_configs <ENV>` to render secrets locally. The default `<ENV>` is `dev`, which is what you should use for local running and testing.

DrsHub uses Gradle as a build tool. Some common Gradle commands you may want to run are
```shell
./gradlew generateSwaggerCode # Generate Swagger code for models and Swagger UI
./gradlew bootRun # Run DrsHub locally (Swagger UI at localhost:8080)
./gradlew test # Run the unit tests
./gradlew jib # Build the DrsHub Docker image
```

### Run Integration Tests
DrsHub uses [TestRunner](https://github.com/DataBiosphere/terra-test-runner) to run its integration tests.
To run the integration test suite, run
```shell
./gradlew runTest --args="suites/FullIntegration.json /tmp/test-results"
```
Adding `--stacktrace` can give you more debugging information, if needed.

### Logging
By default, DrsHub will emit logs in the Stackdriver JSON format. 
To disable this behavior for local development, add `DRSHUB_LOG_APPENDER=Console-Standard` to your environment when running DrsHub.

## Deployment
DrsHub runs in Kubernetes in GCP. Current deployments can be found at:
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