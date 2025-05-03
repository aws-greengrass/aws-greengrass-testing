import os
import subprocess
import time
from typing import Literal


class SystemInterface:

    def check_systemctl_status_for_component(
            self, component_name: str
    ) -> Literal['RUNNING', 'FINISHED', 'NOT_RUNNING']:
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
                if len(output.split("\n")) > 4:
                    active_line = output.split("\n")[2].strip()
                    process_line = output.split("\n")[4].strip()
                    if "Active: active (running)" in active_line:
                        print("Process is active")
                        return "RUNNING"
                    elif ("Active: inactive (dead)"
                          in active_line) and ("status=0/SUCCESS"
                                               in process_line):
                        print("Process is finished")
                        return "FINISHED"

            process.terminate()

            return "NOT_RUNNING"

        except KeyboardInterrupt:
            print("\nStopping monitor...")
            process.terminate()
            return "NOT_RUNNING"
        except Exception as e:
            print(f"Error: {e}")
            return "NOT_RUNNING"

    def stop_systemd_nucleus_lite(self, timeout: int | float) -> bool:
        try:
            cmd = [
                "sudo",
                "systemctl",
                "stop",
                "--with-dependencies",
                "greengrass-lite.target",
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            timeout = time.time() + timeout

            # Call the readline is blocking, set it to non-blocking mode.
            os.set_blocking(process.stdout.fileno(), False)

            while True:
                # Check timeout
                if time.time() > timeout:
                    print(f"Timeout after {timeout} seconds")
                    process.terminate()
                    return False

                output, errors = process.communicate()
                if output:
                    print(output)
                    print(errors)
                    return True

                # Check if process has terminated
                if process.poll() is not None:
                    print("Shutdown process terminated.")
                    return False

                time.sleep(0.01)

        except KeyboardInterrupt:
            print("\nExiting the shutdown attempt...")
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

    def check_systemd_user(self, component_name, timeout: int | float) -> str:
        try:
            cmd = [
                "systemctl", "show", "-p", "User",
                f"ggl.{component_name}.service"
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            timeout = time.time() + timeout

            # Call the readline is blocking, set it to non-blocking mode.
            os.set_blocking(process.stdout.fileno(), False)

            while True:
                # Check timeout
                if time.time() > timeout:
                    print(f"Timeout after {timeout} seconds")
                    process.terminate()
                    return ""

                output, errors = process.communicate()
                if output:
                    print(output)
                    print(errors)
                    return output

                # Check if process has terminated
                if process.poll() is not None:
                    print("Shutdown process terminated.")
                    return ""

                time.sleep(0.01)

        except KeyboardInterrupt:
            print("\nExiting the shutdown attempt...")
            process.terminate()
            return ""
        except Exception as e:
            print(f"Error: {e}")
            return ""
        finally:
            # Ensure process is terminated
            try:
                process.terminate()
            except:
                pass

    def start_systemd_nucleus_lite(self, timeout: int | float) -> bool:
        try:
            cmd = [
                "sudo",
                "systemctl",
                "start",
                "--with-dependencies",
                "greengrass-lite.target",
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            timeout = time.time() + timeout

            # Call the readline is blocking, set it to non-blocking mode.
            os.set_blocking(process.stdout.fileno(), False)

            while True:
                # Check timeout
                if time.time() > timeout:
                    print(f"Timeout after {timeout} seconds")
                    process.terminate()
                    return False

                output, errors = process.communicate()
                if output:
                    print(output)
                    print(errors)
                    return False

                # Check if process has terminated
                if process.poll() is not None:
                    print("Shutdown process terminated.")
                    return False

                time.sleep(0.01)

        except KeyboardInterrupt:
            print("\nExiting the shutdown attempt...")
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

    def monitor_journalctl_for_message(self, service_name: str, message: str,
                                       timeout: int | float) -> bool:
        try:
            cmd = [
                "sudo",
                "journalctl",
                "-xeau",
                service_name,
                "-f",    # Follow mode - shows new entries as they are added
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
