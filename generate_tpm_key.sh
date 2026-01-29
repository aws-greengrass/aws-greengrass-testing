#!/bin/bash
set -e

TPM_KEY_HANDLE="${TPM_KEY_HANDLE:-0x81000004}"
CONFIG_FILE="/etc/greengrass/config.yaml"

# Calculate directories relative to script location
SCRIPT_DIR="$(pwd)"
TEMP_DIR="${SCRIPT_DIR}/TPMCerts"

set_up_tpm() {
    # Check if TPM tools are available
    if ! command -v tpm2_createprimary &> /dev/null || ! command -v tpm2_create &> /dev/null; then
        echo "Error: TPM2 tools not found. Please install tpm2-tools package."
        exit 1
    fi

    # Check if TPM key handle is already in use
    if tpm2_getcap handles-persistent | grep -q "${TPM_KEY_HANDLE}"; then
        echo "Warning: TPM key handle ${TPM_KEY_HANDLE} is already in use. Please change to the available key handle."
        exit 1
    fi

    # Create certificate directory
    mkdir -p "${TEMP_DIR}"

    # Generate TPM private key and CSR
    echo -e "\n=== Generating TPM private key and CSR ==="
    echo "Creating TPM primary key..."
    tpm2_createprimary -C o -c "${TEMP_DIR}/primary.ctx"

    echo "Creating ECC key..."
    tpm2_create -C "${TEMP_DIR}/primary.ctx" -g sha256 -G ecc256 -r "${TEMP_DIR}/device.priv" -u "${TEMP_DIR}/device.pub"

    echo "Loading the key..."
    tpm2_load -C "${TEMP_DIR}/primary.ctx" -r "${TEMP_DIR}/device.priv" -u "${TEMP_DIR}/device.pub" -c "${TEMP_DIR}/device.ctx"

    echo "Making the key persistent..."
    tpm2_evictcontrol -C o -c "${TEMP_DIR}/device.ctx" "${TPM_KEY_HANDLE}"

    echo "Generating CSR with TPM key..."
    openssl req -new -provider tpm2 -key "handle:${TPM_KEY_HANDLE}" \
        -out "${TEMP_DIR}/device.csr" \
        -subj "/CN=TPM_CSR"
    echo "Successfully generated the CSR"

    # Create certificate from CSR
    echo -e "\n=== Creating the certificate from CSR ==="
    echo "Creating certificate from CSR..."
    aws iot create-certificate-from-csr \
    --certificate-signing-request file://"${TEMP_DIR}/device.csr" \
    --set-as-active \
    --certificate-pem-outfile "${TEMP_DIR}/device.pem.crt"
    echo "Successfully created the certificate"

    # Update /etc/greengrass/config.yaml
    if [ -f "${CONFIG_FILE}" ]; then
        sudo cp "${CONFIG_FILE}" "${CONFIG_FILE}.backup"
        sudo sed -i "s|^[[:space:]]*privateKeyPath:.*|    privateKeyPath: \"handle:${TPM_KEY_HANDLE}\"|" "${CONFIG_FILE}"
        sudo sed -i "s|^[[:space:]]*certificateFilePath:.*|    certificateFilePath: \"${TEMP_DIR}/device.pem.crt\"|" "${CONFIG_FILE}"
        echo "Updated ${CONFIG_FILE}"
        echo "Current config.yaml contents:"
        cat "${CONFIG_FILE}"
    else
        echo "Config file not found at ${CONFIG_FILE}"
    fi

    # Configure the user permission
    sudo usermod -a -G tss ggcore

    # Verify user permission
    if groups ggcore | grep -q tss; then
        echo "User ggcore successfully added to tss group"
    else
        echo "Failed to add ggcore to tss group"
    fi

    # Restart the gg-lite
    sudo systemctl restart greengrass-lite.target

}

# Cleanup function to remove TPM key handle and certificates directory
cleanup_tpm() {
    echo "Cleaning up TPM key handle ${TPM_KEY_HANDLE}..."
    if tpm2_getcap handles-persistent | grep -q "${TPM_KEY_HANDLE}"; then
        tpm2_evictcontrol -C o -c "${TPM_KEY_HANDLE}"
        echo "Removed TPM key handle ${TPM_KEY_HANDLE}"
    fi

    echo "Cleaning up TPM certificates directory..."
    rm -rf "${TEMP_DIR}" 2>/dev/null || echo "TPMCerts directory not found or already removed"
}

if [[ "$1" == "setup_tpm" ]]; then
    set_up_tpm
elif [[ "$1" == "cleanup_tpm" ]]; then
    cleanup_tpm
fi
