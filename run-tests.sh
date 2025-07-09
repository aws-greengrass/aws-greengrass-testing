#!/bin/bash

IOT_SCRIPT_PATH="src/iot-setup.py"
GGL_SCRIPT_PATH="src/ggl-setup.py"
JSON_FILE="iot_setup_data.json"
CLI_BIN_PATH="$(pwd)/aws-greengrass-lite/build/bin/ggl-cli"

# Get all test cases
# TODO: get all tests from all test files
get_test_functions() {
    grep -o "test_[[:alnum:]_]*" ./src/aws-greengrass-testing-security.py | sort -u
}

setup_and_cleanup() {
    local test_name=$1
    local test_status=0

    # Setup phase
    echo "Setting up test environment for: $test_name"
    {
        echo "setup python3 venv environment..."
        sudo apt install python3-venv
        python3 -m venv env
        . ./env/bin/activate
        pip install .

        echo "Running IoT setup..."
        python3 "$IOT_SCRIPT_PATH" set_up_core_device --region="$AWS_REGION"

        if [ ! -f "$JSON_FILE" ]; then
            echo "Error: Setup data file not found"
            exit 1
        fi
        THING_GROUP_NAME=$(jq -r '.THING_GROUP_NAME' "$JSON_FILE")
        echo "IoT Setup completed."

        printf "\nRunning Greengrass-Lite setup...\n"
        python3 "$GGL_SCRIPT_PATH" install_greengrass_lite_from_source --id="$COMMIT_ID" --region="$AWS_REGION"
        echo "GGL Setup completed."
    } || {
        echo "Setup failed for test: $test_name"
        return 1
    }

    # Test execution phase
    echo "Executing test: $test_name"
    if ! pytest -q -s -v \
        ./src/aws-greengrass-testing-security.py \
        -k "$test_name" \
        --security_thing_group="$THING_GROUP_NAME" \
        --aws-account="$AWS_ACCOUNT" \
        --s3-bucket="$S3_BUCKET" \
        --region="$AWS_REGION" \
        --ggl-cli-path="$CLI_BIN_PATH"; then
        test_status=1
        echo "Test failed: $test_name"
    fi

    # Cleanup phase (always executed)
    echo "Starting cleanup for test: $test_name"
    {
        printf "\nRunning Greengrass-Lite clean up...\n"
        python3 "$GGL_SCRIPT_PATH" clean_up
        echo "GGL clean-up completed."

        printf "\nRunning IoT cleanup...\n"
        python3 "$IOT_SCRIPT_PATH" clean_up --region="$AWS_REGION" --thing_group="$THING_GROUP_NAME"
        echo "IoT cleanup completed."
    } || {
        echo "Warning: Cleanup failed for test: $test_name"
        # Don't override test failure status with cleanup failure
        if [ $test_status -eq 0 ]; then
            test_status=1
        fi
    }

    return $test_status
}

# Get all test functions and run setup_and_cleanup for each
main() {
    local overall_status=0
    test_functions=($(get_test_functions))

    # Run setup_and_cleanup for each test function
    for test_func in "${test_functions[@]}"; do
        echo "=============================================="
        echo "Starting test suite for: $test_func"
        echo "=============================================="
        if ! setup_and_cleanup "$test_func"; then
            echo "Test suite failed: $test_func"
            overall_status=1
            break
        fi
    done
    
    if [ $overall_status -eq 0 ]; then
        echo "All tests completed successfully"
    else
        echo "Testing failed"
    fi

    exit $overall_status
}

# Parse command line arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --aws-account=*)
        AWS_ACCOUNT="${1#*=}"
        ;;
        --aws-region=*)
        AWS_REGION="${1#*=}"
        ;;
        --s3-bucket=*)
        S3_BUCKET="${1#*=}"
        ;;
        --commit-id=*)
        COMMIT_ID="${1#*=}"
        ;;
        *)
        echo "Unknown parameter passed: $1"
        exit 1
        ;;
    esac
    shift
done


# Run the main function
main
