# Platforms

The Greengrass testing framework attempts to be as platform independent as possible, but there are cases
where abstractions are needed, especially for remote device under test (DUT).

## The API

The API module exposes programmatic support for interacting with the operating system on the device. Key
abstractions are `files` and `commands` for remote filesystem and process management, respectively.

## The PillBox

The Pillbox module provides a JVM binary to provide platform independent FS commands for predictable
interaction using a remote DUT.