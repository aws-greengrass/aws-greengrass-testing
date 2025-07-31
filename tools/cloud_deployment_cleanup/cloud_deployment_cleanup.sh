#!/bin/bash

# Check if the deployment name is provided
if [ $# -eq 0 ]; then
    echo "Please provide the Greengrass deployment name as an argument."
    exit 1
fi

DEPLOYMENT_NAME="$1"
total_deleted=0

# Function to cancel a deployment
cancel_deployment() {
    local deployment_id="$1"
    local output
    output=$(aws greengrassv2 cancel-deployment --deployment-id "$deployment_id" 2>&1)
    local exit_code=$?

    if echo "$output" | grep -q "ThrottlingException"; then
        return 2  # Special return code for throttling
    elif [ $exit_code -ne 0 ]; then
        echo "Cancel error: $output"
        return 1
    fi

    return 0
}

# Function to delete a deployment
delete_deployment() {
    local deployment_id="$1"
    local output
    output=$(aws greengrassv2 delete-deployment --deployment-id "$deployment_id" 2>&1)
    local exit_code=$?

    if echo "$output" | grep -q "ThrottlingException"; then
        return 2  # Special return code for throttling
    elif echo "$output" | grep -q "must be canceled before deletion"; then
        return 3  # Special return code for cancellation needed
    elif [ $exit_code -ne 0 ]; then
        echo "Delete error: $output"
        return 1
    fi

    return 0
}

while true; do
    # Get all deployment IDs with the specified name
    deployment_ids=$(aws greengrassv2 list-deployments \
        --query "deployments[?deploymentName=='$DEPLOYMENT_NAME'].deploymentId" \
        --output text)

    # If no deployments found, we're done
    if [ -z "$deployment_ids" ] || [ "$deployment_ids" == "None" ]; then
        echo "No deployments found with name: $DEPLOYMENT_NAME"
        break
    fi

    # Convert to array and delete each deployment
    for deployment_id in $deployment_ids; do
        echo "Found deployment ID: $deployment_id"
        while true; do
            delete_deployment "$deployment_id"
            delete_result=$?

            if [ $delete_result -eq 0 ]; then
                ((total_deleted++))
                echo "Deleted deployment $deployment_id (Total deletions: $total_deleted)"
                break
            elif [ $delete_result -eq 2 ]; then
                echo "Throttling detected. Waiting for 10 seconds..."
                sleep 10
                continue
            elif [ $delete_result -eq 3 ]; then
                echo "Canceling deployment $deployment_id first..."
                while true; do
                    cancel_deployment "$deployment_id"
                    cancel_result=$?
                    if [ $cancel_result -eq 0 ]; then
                        echo "Canceled deployment $deployment_id"
                        sleep 2
                        break
                    elif [ $cancel_result -eq 2 ]; then
                        echo "Throttling detected during cancel. Waiting for 10 seconds..."
                        sleep 10
                        continue
                    else
                        echo "Failed to cancel deployment $deployment_id"
                        exit 1
                    fi
                done
                continue
            else
                echo "Failed to delete deployment $deployment_id"
                exit 1
            fi
        done

        # Small delay to avoid API rate limiting
        sleep 1
    done

    # Small delay before checking for more deployments
    sleep 2
done
