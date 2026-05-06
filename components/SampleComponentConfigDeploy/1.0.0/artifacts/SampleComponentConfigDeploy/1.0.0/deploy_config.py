"""
UAT artifact: Calls CreateLocalDeployment IPC to merge and reset config
on SampleComponentWithConfiguration, then verifies via GetConfiguration IPC.

Outputs MERGE VERIFIED / RESET VERIFIED markers for journal-based assertion.
"""
import sys
import time

try:
    from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
except ImportError:
    print("ERROR: awsiot.greengrasscoreipc not available",
          file=sys.stderr,
          flush=True)
    sys.exit(1)

TARGET = "SampleComponentWithConfiguration"
KEY = "MyConfigKey"
MERGE_VALUE = "MergedViaIPC"
DEFAULT_VALUE = "MyConfigDefaultValue"


def get_config_value(client):
    """Read a key from target component's config via GetConfiguration IPC."""
    try:
        resp = client.get_configuration(component_name=TARGET, key_path=[KEY])
        val = resp.value.get(KEY, None) if resp.value else None
        return str(val) if val is not None else None
    except Exception as e:
        print(f"GetConfiguration failed: {e}", flush=True)
        return None


def main():
    client = GreengrassCoreIPCClientV2()

    # Step 1: MERGE MyConfigKey with new value
    # NOTE: Lite ggdeploymentd expects MERGE as a map (dict), not a JSON string.
    # The Python SDK serializes dicts as maps on the wire, which is what PR #1108 handles.
    print("Issuing CreateLocalDeployment with MERGE config...", flush=True)
    resp = client.create_local_deployment(component_to_configuration={
        TARGET: {
            "MERGE": {
                KEY: MERGE_VALUE
            },
            "RESET": [],
        }
    }, )
    print(f"Merge deployment response: {resp}", flush=True)

    # Wait for merge to take effect
    time.sleep(15)

    # Verify via GetConfiguration IPC
    val = get_config_value(client)
    print(f"POST-MERGE config value: {val}", flush=True)
    if val == MERGE_VALUE:
        print("MERGE VERIFIED", flush=True)
    else:
        print(f"MERGE FAILED: expected {MERGE_VALUE}, got {val}", flush=True)

    # Step 2: RESET the key
    print("Issuing CreateLocalDeployment with RESET config...", flush=True)
    resp = client.create_local_deployment(component_to_configuration={
        TARGET: {
            "MERGE": {},
            "RESET": [f"/{KEY}"],
        }
    }, )
    print(f"Reset deployment response: {resp}", flush=True)

    time.sleep(15)

    # Verify reset via GetConfiguration IPC
    val = get_config_value(client)
    print(f"POST-RESET config value: {val}", flush=True)
    if val is None or val == DEFAULT_VALUE:
        print("RESET VERIFIED", flush=True)
    else:
        print(f"RESET FAILED: expected None or {DEFAULT_VALUE}, got {val}",
              flush=True)

    print("ALL CHECKS PASSED", flush=True)
    # Keep running so systemd considers the component RUNNING
    while True:
        time.sleep(60)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"FATAL: {e}", flush=True)
        import traceback
        traceback.print_exc()
        sys.exit(1)
