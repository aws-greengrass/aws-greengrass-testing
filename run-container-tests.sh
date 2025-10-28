#!/bin/bash

# Test categories
if [ -n "$1" ]; then
    IFS=',' read -ra CATEGORIES <<< "$1"
else
    CATEGORIES=("security runtime component deployment fleet-status")
fi

# Run tests for each category
for category in "${CATEGORIES[@]}"; do
    echo "=========================================="
    echo "Starting tests for category: $category"
    echo "=========================================="

    rm -rf $PWD/env

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
        -v "$PWD:/aws-greengrass-testing" \
        --replace \
        --name "buildtestcontainer-$category" \
        buildtestcontainer:latest
    
    podman exec -w /aws-greengrass-testing "buildtestcontainer-$category" bash -c "/aws-greengrass-testing/run-tests.sh --aws-account=$AWS_ACCOUNT --s3-bucket=$S3_BUCKET --commit-id=$COMMIT_ID --aws-region=$AWS_DEFAULT_REGION --test-category=$category"
    
    podman stop buildtestcontainer-$category
    podman rm buildtestcontainer-$category
    
    echo "Completed tests for category: $category"
    echo ""
done

rm -rf "$DOWNLOAD_DIR"
echo "All test categories completed!"
