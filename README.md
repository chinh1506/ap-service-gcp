
[//]: # (Deploy cloud run service)
gcloud run deploy --source . --env-vars-file=./.env

[//]: # (Deploy cloud run Job)
gcloud run jobs deploy ap-job \                              
--source . \
--tasks 10 \
--set-env-vars SLEEP_MS=10000 \
--set-env-vars FAIL_RATE=0.1 \
--max-retries 5 \
--region asia-southeast1 \
--project=ethereal-hub-483507-d4 --set-env-vars SPRING_PROFILES_ACTIVE=job
--env-vars-file=./.env