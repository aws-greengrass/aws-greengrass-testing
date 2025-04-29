import os
import subprocess
import time


class SystemInterface:

    def check_systemctl_status_for_component(self, component_name):
        try:
            cmd = [
                "sudo",
                "systemctl",
                "status",
                f"ggl.{component_name}.service",
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            output, errors = process.communicate()
            if output:
                if len(output.split("\n")) > 2:
                    if "Active: active (running)" in output.split(
                            "\n")[2].strip():
                        print(f"Process is active")
                        return "RUNNING"

            process.terminate()

            return "NOT_RUNNING"

        except KeyboardInterrupt:
            print("\nStopping monitor...")
            process.terminate()
            return "NOT_RUNNING"
        except Exception as e:
            print(f"Error: {e}")
            return "NOT_RUNNING"

    def monitor_journalctl_for_message(self, service_name, message, timeout):
        try:
            cmd = [
                "sudo",
                "journalctl",
                "-xeau",
                service_name,
                "-f",  # Follow mode - shows new entries as they are added
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            print(f"Monitoring logs for {service_name}...")
            timeout = time.time() + timeout

            # Call the readline is blocking, set it to non-blocking mode.
            os.set_blocking(process.stdout.fileno(), False)

            while True:
                # Check timeout
                if time.time() > timeout:
                    print(f"Timeout after {timeout} seconds")
                    process.terminate()
                    return False

                output = process.stdout.readline()
                if output:
                    if message in output.strip():
                        print(f"Found log")
                        return True

                # Check if process has terminated
                if process.poll() is not None:
                    print("journalctl process killed.")
                    return False

                time.sleep(0.01)

        except KeyboardInterrupt:
            print("\nStopping monitor...")
            process.terminate()
            return False
        except Exception as e:
            print(f"Error: {e}")
            return False
        finally:
            # Ensure process is terminated
            try:
                process.terminate()
            except:
                pass
