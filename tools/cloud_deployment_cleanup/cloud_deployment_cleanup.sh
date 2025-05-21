#!/bin/bash

# Check if the deployment name is provided
if [ $# -eq 0 ]; then
    echo "Please provide the Greengrass deployment name as an argument."
    exit 1
fi

DEPLOYMENT_NAME="$1"
total_deleted=0

# Function to delete a deployment
delete_deployment() {
    local deployment_id="$1"
    local output
    output=$(aws greengrassv2 delete-deployment --deployment-id "$deployment_id" 2>&1)
    local exit_code=$?

    if echo "$output" | grep -q "ThrottlingException"; then
        echo "Throttling detected. Waiting for 10 seconds..."
        sleep 10
        return 2  # Special return code for throttling
    elif [ $exit_code -ne 0 ]; then
        echo "Error: $output"
        return 1
    fi

    return 0
}

while true; do
    # Get current deployment ID
    deployment_id=$(aws greengrassv2 list-deployments \
        --query "deployments[?deploymentName=='$DEPLOYMENT_NAME'].deploymentId" \
        --output text)

    # If no deployment found, we're done
    if [ -z "$deployment_id" ] || [ "$deployment_id" == "None" ]; then
        break
    fi

    echo "Found deployment ID: $deployment_id"

    while true; do
        delete_deployment "$deployment_id"
        delete_result=$?

        if [ $delete_result -eq 0 ]; then
            ((total_deleted++))
            echo "Deleted revision for deployment $deployment_id (Total deletions: $total_deleted)"
            break
        elif [ $delete_result -eq 2 ]; then
            continue  # Retry after throttling
        else
            echo "Failed to delete deployment $deployment_id"
            exit 1
        fi
    done

    # Small delay to avoid API rate limiting
    sleep 1
done
