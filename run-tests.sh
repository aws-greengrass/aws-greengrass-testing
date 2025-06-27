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

    echo "Running IoT setup..."
    python3 "$IOT_SCRIPT_PATH" set_up_core_device --region="$AWS_REGION"

    # Read from JSON file
    if [ -f "$JSON_FILE" ]; then
        THING_GROUP_NAME=$(jq -r '.THING_GROUP_NAME' "$JSON_FILE")
        echo "IoT Setup completed."
    else
        echo "Error: Setup data file not found"
        exit 1
    fi

    # Set up greengrass-lite
    # TODO: delete the pause
    read -p "Press enter to continue with Greengrass-Lite setup..."

    printf "\nRunning Greengrass-Lite setup...\n"
    python3 "$GGL_SCRIPT_PATH" install_greengrass_lite_from_source --id="$COMMIT_ID" --region="$AWS_REGION"
    echo "GGL Setup completed."

    # Run tests
    read -p "Press enter to continue with test cases running..."
    python3 -m venv env
    . ./env/bin/activate
    pip install .
    pytest -q -s -v \
        ./src/aws-greengrass-testing-security.py \
        -k "$test_name" \
        --security_thing_group="$THING_GROUP_NAME" \
        --aws-account="$AWS_ACCOUNT" \
        --s3-bucket="$S3_BUCKET" \
        --region="$AWS_REGION" \
        --ggl-cli-path="$CLI_BIN_PATH"


    # Add a pause before ggl cleanup
    read -p "Press enter to continue with Greengrass-Lite cleanup..."
    printf "\nRunning Greengrass-Lite clean up...\n"
    python3 "$GGL_SCRIPT_PATH" clean_up
    echo "GGL clean-up completed."

    read -p "Press enter to continue with IoT cleanup..."
    printf "\nRunning IoT cleanup...\n"
    python3 "$IOT_SCRIPT_PATH" clean_up --region="$AWS_REGION" --thing_group="$THING_GROUP_NAME"
    echo "IoT cleanup completed."

}

# Get all test functions and run setup_and_cleanup for each
main() {
    # Get all test functions
    test_functions=($(get_test_functions))

    # Run setup_and_cleanup for each test function
    for test_func in "${test_functions[@]}"; do
        read -p ">> Press enter to run setup and cleanup for $test_func"
        setup_and_cleanup "$test_func"
    done
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
