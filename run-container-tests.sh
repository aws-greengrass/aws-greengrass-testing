#!/bin/bash

set -euo pipefail

# Parse arguments
TEST_NAME=""
if [ -n "${1:-}" ]; then
    IFS=',' read -ra CATEGORIES <<< "$1"
else
    CATEGORIES=("security" "runtime" "component" "deployment" "fleet-status")
fi

if [ -n "${2:-}" ]; then
    TEST_NAME="$2"
fi

# Run tests for each category
for category in "${CATEGORIES[@]}"; do
    LOG_FILE="test-${category}-$(date +%Y%m%d-%H%M%S).log"
    CONTAINER_NAME="buildtestcontainer-${category}-$(date +%s)"

    echo "=========================================="
    echo "Starting tests for category: $category"
    if [ -n "$TEST_NAME" ]; then
        echo "Running specific test: $TEST_NAME"
    fi
    echo "Logging to: $LOG_FILE"
    echo "=========================================="

    {
        podman run -d \
            --systemd=always \
            --tmpfs /tmp \
            --tmpfs /run \
            -v /sys/fs/cgroup:/sys/fs/cgroup:ro \
            -e AWS_ACCESS_KEY_ID \
            -e AWS_SECRET_ACCESS_KEY \
            -e AWS_DEFAULT_REGION \
            -e COMMIT_ID \
            -e AWS_ACCOUNT \
            -e S3_BUCKET \
            -v "$PWD:/aws-greengrass-testing:ro" \
            --name "$CONTAINER_NAME" \
            buildtestcontainer:latest

        sleep 3

        podman exec -w /aws-greengrass-testing "$CONTAINER_NAME" bash -c "/aws-greengrass-testing/run-tests.sh --aws-account=$AWS_ACCOUNT --s3-bucket=$S3_BUCKET --commit-id=$COMMIT_ID --aws-region=$AWS_DEFAULT_REGION --test-category=$category ${TEST_NAME:+--test-name=$TEST_NAME}" || echo "Tests failed for category: $category"

        podman stop "$CONTAINER_NAME" || true
        podman rm "$CONTAINER_NAME" || true
    } 2>&1 | tee "$LOG_FILE"

    echo "Completed tests for category: $category"
    echo ""
done

echo "All test categories completed!"
