# Cloud Deployment Cleanup Script

This script cleans the revisions of a deployment targeted to one thing group.

For the tool to work efficiently, the deployment must have same names on
different revisions

## How to execute

You can execute the script by exporting the AWS credentials for the target
account in your shell and then run the following command:

```
export AWS_ACCESS_KEY_ID=[REPLACE HERE]
export AWS_SECRET_ACCESS_KEY=[REPLACE HERE]
export AWS_SESSION_TOKEN=[REPLACE HERE]
export AWS_DEFAULT_REGION=us-west-2

chmod +x ./cloud_deployment_cleanup.sh
./cloud_deployment_cleanup.sh "<name of deployment>"
```

### Note: The script will attempt to delete all the deployments with the same name, one by one
