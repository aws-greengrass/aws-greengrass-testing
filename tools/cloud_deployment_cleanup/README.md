# Cloud Deployment Cleanup Script

This script cleans the revisions of a deployment targeted to one thing group.

For the tool to work correctly, the deployment:

- Must not have a duplicate with the same name but different thing group
- Must have different names on different revisions

## How to execute

You can execute the script by exporting the AWS credentials for the target
account in your shell and then run the following command:

```
chmod +x ./cloud_deployment_cleanup.sh
./cloud_deployment_cleanup.sh "<name of deployment>"
```
