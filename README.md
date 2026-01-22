
[//]: # (Deploy cloud run service)
gcloud run deploy ap-service-gcp \
--source . \
--region asia-southeast1 \
--env-vars-file=./service-prod.env \
--set-secrets="/app/secrets/s0/credentials-0.json=credentials-0:latest,/app/secrets/s1/credentials-1.json=credentials-1:latest,/app/secrets/s2/credentials-2.json=credentials-2:latest,/app/secrets/s3/credentials-3.json=credentials-3:latest,/app/secrets/s4/credentials-4.json=credentials-4:latest"

[//]: # (Deploy cloud run Job)
gcloud run jobs deploy ap-job \
--source . \
--tasks 10 \
--max-retries 5 \
--region asia-southeast1 \
--project=ethereal-hub-483507-d4 \
--env-vars-file=./job-prod.env \
--set-secrets="/app/secrets/s0/credentials-0.json=credentials-0:latest,/app/secrets/s1/credentials-1.json=credentials-1:latest,/app/secrets/s2/credentials-2.json=credentials-2:latest,/app/secrets/s3/credentials-3.json=credentials-3:latest,/app/secrets/s4/credentials-4.json=credentials-4:latest"