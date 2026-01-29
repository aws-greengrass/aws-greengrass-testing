#!/bin/bash
set -e

# Variables
REGION="${AWS_DEFAULT_REGION:-us-west-2}"
STACK_NAME="GreengrassFleetProvisioning-TPM"
TPM_KEY_HANDLE="${TPM_KEY_HANDLE:-0x81000000}"
CONFIG_FILE="/etc/greengrass/config.yaml"

# Paths
GGL_WORKSPACE="/tmp/aws-greengrass-testing-workspace/aws-greengrass-lite"
CLAIM_SCRIPT="${GGL_WORKSPACE}/docs/fleet_provisioning/generate_claim_tpm.sh"
TEMP_DIR="${GGL_WORKSPACE}/TPMFleetCerts"

setup_fleet_provisioning() {
    # Run generate_claim_tpm.sh (handles stack, TPM key, cert, policy, endpoints)
    cd "${GGL_WORKSPACE}/docs/fleet_provisioning"
    export AWS_DEFAULT_REGION="${REGION}"
    export TPM_KEY_HANDLE="${TPM_KEY_HANDLE}"
    export STACK_NAME="${STACK_NAME}"
    bash "${CLAIM_SCRIPT}"

    # Get stack outputs for config.yaml
    PROVISIONING_TEMPLATE_NAME=$(aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query "Stacks[0].Outputs[?OutputKey=='ProvisioningTemplateName'].OutputValue" --output text --region "${REGION}")
    TOKEN_EXCHANGE_ROLE_ALIAS=$(aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query "Stacks[0].Outputs[?OutputKey=='TokenExchangeRoleAlias'].OutputValue" --output text --region "${REGION}")
    IOT_DATA_ENDPOINT=$(aws iot describe-endpoint --endpoint-type iot:Data-ATS --region "${REGION}" --output text)
    IOT_CRED_ENDPOINT=$(aws iot describe-endpoint --endpoint-type iot:CredentialProvider --region "${REGION}" --output text)

    # Create config.yaml for fleet provisioning
    echo -e "\n=== Creating config.yaml for fleet provisioning ==="
    # Get MAC address - try multiple methods for compatibility
    MAC_ADDRESS=$(ip link show | awk '/link\/ether/ {print $2; exit}' | tr ':' '_')
    if [ -z "$MAC_ADDRESS" ]; then
        for iface in /sys/class/net/*; do
            iface_name=$(basename "$iface")
            if [ "$iface_name" != "lo" ] && [ -f "$iface/address" ]; then
                MAC_ADDRESS=$(tr ':' '_' < "$iface/address")
                break
            fi
        done
    fi
    echo "Using SerialNumber: $MAC_ADDRESS"

    sudo mkdir -p /etc/greengrass
    sudo tee "${CONFIG_FILE}" > /dev/null << EOF
---
system:
  privateKeyPath: ""
  certificateFilePath: ""
  rootCaPath: ""
  rootPath: "/var/lib/greengrass"
  thingName: ""
services:
  aws.greengrass.NucleusLite:
    componentType: "NUCLEUS"
    configuration:
      awsRegion: "${REGION}"
      iotCredEndpoint: ""
      iotDataEndpoint: ""
      iotRoleAlias: "${TOKEN_EXCHANGE_ROLE_ALIAS}"
      runWithDefault:
        posixUser: "ggcore:ggcore"
      greengrassDataPlanePort: "8443"
  aws.greengrass.fleet_provisioning:
    configuration:
      iotDataEndpoint: "${IOT_DATA_ENDPOINT}"
      iotCredEndpoint: "${IOT_CRED_ENDPOINT}"
      rootCaPath: "${TEMP_DIR}/AmazonRootCA1.pem"
      claimKeyPath: "handle:${TPM_KEY_HANDLE}"
      claimCertPath: "${TEMP_DIR}/certificate.pem.crt"
      csrCommonName: "aws-greengrass-nucleus-lite"
      templateName: "${PROVISIONING_TEMPLATE_NAME}"
      templateParams:
        SerialNumber: "${MAC_ADDRESS}"
EOF

    echo "Initial config.yaml:"
    sudo cat "${CONFIG_FILE}"

    # Copy config to aws-greengrass-testing directory
    sudo cp "${CONFIG_FILE}" "/home/ubuntu/repos/aws-greengrass-testing/config.yaml"


    # Configure user permissions first
    sudo groupadd ggcore 2>/dev/null || true
    sudo useradd -Ng ggcore ggcore 2>/dev/null || true
    sudo usermod -a -G tss ggcore

    # Verify user permission
    if groups ggcore | grep -q tss; then
        echo "User ggcore successfully added to tss group"
    else
        echo "Failed to add ggcore to tss group"
    fi

    # Deploy config and run fleet provisioning
    sudo mkdir -p /var/lib/greengrass/credentials
    sudo chown -R ggcore:ggcore /var/lib/greengrass
    sudo chmod 755 /var/lib/greengrass/credentials
    sudo rm -rf /var/lib/greengrass/config.db

    # Build and install Greengrass Lite
    echo -e "\n=== Building and installing Greengrass Lite ==="
    cd "${GGL_WORKSPACE}"

    # Build if not already built
    if [ ! -d "build" ]; then
        cmake -B build -D CMAKE_INSTALL_PREFIX=/usr/local -D CMAKE_BUILD_TYPE=MinSizeRel -DGGL_LOG_LEVEL=DEBUG
        make -C build -j"$(nproc)"
    fi

    # Install
    sudo make -C build install

    # Run nucleus to set up systemd services
    sudo ./misc/run_nucleus
    echo "Greengrass services installed"
    sleep 20

    echo -e "\n=== Running fleet provisioning ==="
    sudo /usr/local/bin/fleet-provisioning
    sleep 20

    # Restart greengrass after provisioning
    echo -e "\n=== Restarting Greengrass after provisioning ==="
    sudo systemctl restart greengrass-lite.target
    sleep 20

    echo "Fleet provisioning setup complete"
}

cleanup_fleet_provisioning() {
    echo "Cleaning up fleet provisioning resources..."

    # Remove TPM key handle
    if tpm2_getcap handles-persistent | grep -q "${TPM_KEY_HANDLE}"; then
        tpm2_evictcontrol -C o -c "${TPM_KEY_HANDLE}"
        echo "Removed TPM key handle ${TPM_KEY_HANDLE}"
    fi

    POLICY_NAME="FleetProvisioningPolicy-GreengrassFleetProvisioning-TPM"
    # Get all certificate ARNs attached to the policy
    CERT_ARNS=$(aws iot list-targets-for-policy --policy-name "$POLICY_NAME" --query 'targets[]' --output text)

    for CERT_ARN in $CERT_ARNS; do
        CERT_ID=$(echo "$CERT_ARN" | cut -d'/' -f2)
        echo "Processing certificate: $CERT_ID"
        # Detach policy
        aws iot detach-policy --policy-name "$POLICY_NAME" --target "$CERT_ARN"
        # Deactivate certificate
        aws iot update-certificate --certificate-id "$CERT_ID" --new-status INACTIVE
        # Delete certificate
        aws iot delete-certificate --certificate-id "$CERT_ID" --force-delete
    done
    echo "Certs cleanup complete. You can now delete the CloudFormation stack."

    # Delete CloudFormation stack
    aws cloudformation delete-stack --stack-name ${STACK_NAME} --region "${REGION}" 2>/dev/null || true

    echo "Cloudformation cleanup completed"
}

if [[ "$1" == "setup_fleet_provisioning" ]]; then
    setup_fleet_provisioning
elif [[ "$1" == "cleanup_fleet_provisioning" ]]; then
    cleanup_fleet_provisioning
fi
