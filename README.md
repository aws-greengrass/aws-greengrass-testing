# AWS Greengrass Lite Testing Framework

This Greengrass Lite testing framework is a collection of building blocks that
supports end-to-end automation using pytest. It provides automated testing of
component deployment, security, runtime behavior, etc.

## Overview

This testing framework validates AWS Greengrass Lite functionality across
multiple scenarios. These tests are automatically executed on every pull request
merge to the
[aws-greengrass-lite](https://github.com/aws-greengrass/aws-greengrass-lite)
repository via the
[UAT GitHub workflow](https://github.com/aws-greengrass/aws-greengrass-lite/blob/main/.github/workflows/uat.yml).

**Test coverage includes:**

| Feature                            | Category             |
| :--------------------------------- | -------------------- |
| Component                          | component            |
| Deployment (Local/Cloud)           | deployment           |
| Fleet status                       | fleet-status         |
| Hardware Security Module (HSM/TPM) | hsm                  |
| Security                           | security             |
| System log forwarder               | system-log-forwarder |
| Runtime                            | runtime              |

## Prerequisites

- **Python 3.10+** with venv support
- **AWS CLI** configured with appropriate permissions
- **Linux environment** with systemd support
- **Required AWS resources:**
  - AWS Account with Greengrass permissions
  - S3 bucket for artifact storage
  - IoT Core access for device management

## Quick Start

### 1. Configure AWS Credentials

```bash
# Set up AWS credentials
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="your-aws-region"

# OR configure through AWS CLI
aws configure
```

### 2. Run Tests

```bash
# Run all tests in a category
./run-tests.sh --aws-account=123456789012 \
               --s3-bucket=your-test-bucket \
               --commit-id=commit-id-you-test \
               --aws-region=your-aws-region \
               --test-category=component # See the category on the table above

# Run specific tests
./run-tests.sh --aws-account=123456789012 \
               --s3-bucket=your-test-bucket \
               --commit-id=commit-id-you-test \
               --aws-region=your-aws-region \
               --test-category=deployment \
               --test-name=test_Deployment_3_T1,test_Deployment_3_T2 # Split by comma for more than one test
```

## Command Line Options

| Option            | Description                               | Required |
| ----------------- | ----------------------------------------- | -------- |
| `--aws-account`   | AWS Account ID (12 digits)                | Yes      |
| `--s3-bucket`     | S3 bucket for test artifacts              | Yes      |
| `--commit-id`     | Greengrass Lite commit id to test         | Yes      |
| `--aws-region`    | AWS region for testing                    | Yes      |
| `--test-category` | Test category to run                      | Yes      |
| `--test-name`     | Specific test(s) to run (comma-separated) | No       |

## Container Testing

Run tests in isolated containers using Podman:

```bash
# Set environment variables
export AWS_ACCESS_KEY_ID="your-key"
export AWS_SECRET_ACCESS_KEY="your-secret"
export AWS_DEFAULT_REGION="your-region"
export COMMIT_ID="your-commit-id"
export AWS_ACCOUNT="123456789012"
export S3_BUCKET="your-test-bucket"

# Run all test categories
./run-container-tests.sh

# Run specific categories
./run-container-tests.sh "component,deployment"

# Run specific test in a category
./run-container-tests.sh "security" "test_Security_6_T6"
```

## Test Framework Architecture

### Core Components

- **GGLSetup.py** - Greengrass Lite installation and setup
- **GGTestUtils.py** - Greengrass Deployment and component management utilities
- **IoTUtils.py** - AWS IoT Core operations (devices, certificates, policies)
- **SystemInterface.py** - System-level operations and monitoring

### Test Structure

Each test follows this pattern:

1. **Setup** - Create IoT devices, install Greengrass Lite
2. **Execute** - Deploy components, run test scenarios
3. **Verify** - Check deployment status, component behavior, logs
4. **Cleanup** - Remove deployments, delete AWS resources

### Automatic Cleanup

The framework automatically cleans up:

- Greengrass deployments and components
- IoT devices, certificates, and policies
- S3 artifacts and test files
- Local system state and processes

## Development

### Adding New Tests

1. Create test components in `components/YourComponent/`
2. Add test functions to appropriate test category file e.g.
   `aws-greengrass-testing-<category>.py`
3. Follow the existing fixture pattern for setup/cleanup
4. Use the GGTestUtils and IoTUtils classes for AWS operations

### Test Naming Convention

- Test files: `aws-greengrass-testing-{category}.py`
- Test functions: `test_{Category}_{TestNumber}_T{Variant}`
- Example: `test_Component_12_T1`, `test_Deployment_3_T2`

## Contributing

1. Follow existing code patterns and naming conventions
2. Ensure proper cleanup in test fixtures
3. Add appropriate error handling and logging
4. Test both success and failure scenarios
5. Update documentation for new test categories

## License

Apache License 2.0 - See LICENSE file for details.
