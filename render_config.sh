ENV=${1:-dev}

if [[ $ENV == dev ]]; then
    GCLOUD_PROJECT=broad-dsde-dev
elif [[ $ENV == integration ]]; then
    GCLOUD_PROJECT=broad-dsde-qa
else
    echo "Invalid environment: $ENV"
    exit 1
fi

SERVICE_OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/integration/src/main/resources/rendered"

GET_SECRET="gcloud secrets versions access latest --project $GCLOUD_PROJECT --secret"

$GET_SECRET drshub-ras-mtls-client-cert >"$SERVICE_OUTPUT_LOCATION/ras-mtls-client.crt"
$GET_SECRET drshub-ras-mtls-client-key >"$SERVICE_OUTPUT_LOCATION/ras-mtls-client.key"
$GET_SECRET drshub-swagger-client-id >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"


$GET_SECRET firecloud-sa >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"
