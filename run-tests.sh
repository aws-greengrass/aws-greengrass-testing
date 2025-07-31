#!/bin/bash

CLI_BIN_PATH="$(pwd)/aws-greengrass-lite/build/bin/ggl-cli"

# Arrays to track test results
declare -a PASSED_TESTS=()
declare -a FAILED_TESTS=()

# Get all test cases
get_test_functions() {
    local test_file="./src/aws-greengrass-testing-$TEST_CATEGORY.py"
    # Check if the file exists
    if [ ! -f "$test_file" ]; then
        echo "Error: Test file $test_file does not exist" >&2
        return 1
    fi
    grep -o "test_[[:alnum:]_]*" "$test_file" | sort -u
}

setup_and_cleanup() {
    local test_name=$1
    local test_status=0

    # Setup phase
    echo "Setting up python3 venv environment"
    {
        sudo apt install python3-venv
        python3 -m venv env
        # shellcheck source=/dev/null
        . ./env/bin/activate
        pip install .
    } || {
        echo "Setup failed for test: $test_name"
        FAILED_TESTS+=("$test_name (Setup Failed)")
        return 1
    }

    # Test execution phase
    echo "Executing test: $test_name"
    if ! pytest -q -s -v \
        ./src/aws-greengrass-testing-"$TEST_CATEGORY".py \
        -k "$test_name" \
        --commit-id="$COMMIT_ID" \
        --aws-account="$AWS_ACCOUNT" \
        --s3-bucket="$S3_BUCKET" \
        --region="$AWS_REGION" \
        --ggl-cli-path="$CLI_BIN_PATH"; then
        test_status=1
        echo "Test failed: $test_name"
        FAILED_TESTS+=("$test_name")
    else
        PASSED_TESTS+=("$test_name")
    fi

    return $test_status
}

# Print test report
print_report() {
    echo ""
    echo "=============================================="
    echo "              TEST EXECUTION REPORT           "
    echo "=============================================="
    echo "Total tests: $((${#PASSED_TESTS[@]} + ${#FAILED_TESTS[@]}))"
    echo "Passed: ${#PASSED_TESTS[@]}"
    echo "Failed: ${#FAILED_TESTS[@]}"
    echo ""

    if [ ${#PASSED_TESTS[@]} -gt 0 ]; then
        echo "PASSED TESTS:"
        for test in "${PASSED_TESTS[@]}"; do
            echo "✅ $test"
        done
        echo ""
    fi

    if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
        echo "FAILED TESTS:"
        for test in "${FAILED_TESTS[@]}"; do
            echo "❌ $test"
        done
        echo ""
    fi
}

# Get all test functions and run setup_and_cleanup for each
main() {
    local overall_status=0
    mapfile -t test_functions < <(get_test_functions)

    # Run setup_and_cleanup for each test function
    for test_func in "${test_functions[@]}"; do
        echo "=============================================="
        echo "Starting test suite for: $test_func"
        echo "=============================================="
        if ! setup_and_cleanup "$test_func"; then
            echo "Test suite failed: $test_func"
            overall_status=1
        fi
    done

    # Print the test report
    print_report

    if [ $overall_status -eq 0 ] && [ ${#FAILED_TESTS[@]} -eq 0 ]; then
        echo "All tests completed successfully"
    else
        echo "Some tests failed. Check the report above for details."
        overall_status=1
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
        --test-category=*)
        TEST_CATEGORY="${1#*=}"
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
