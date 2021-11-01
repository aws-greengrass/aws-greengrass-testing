# Pillbox

From wikipedia:

> A pillbox is a type of blockhouse, or concrete dug-in guard-post, normally equipped with loopholes through which defenders can fire weapons. It is in effect a trench firing step, hardened to protect against small-arms fire and grenades, and raised to improve the field of fire.

This pillbox is a JVM based executable to allow platform independent
calls, especially around the file system and process management. The
`pillbox` binary is 400KB in size and works on all major OS platforms
that support a JVM.

## File System

The entry point being `files`:

```
Usage: pillbox files [-h] [COMMAND]
Platform independent file system interaction.
  -h, --help   Displays help usage information
Commands:
  cat     Reads a file from the local filesystem.
  find    List all files and directories.
  exists  Returns successfully if the path exists.
  rm      Remove files and directories.
```

## Process Management

The entry point being `process`:

```
Usage: pillbox process [-h] [COMMAND]
Platform independent process management utility
  -h, --help   Displays help usage information
Commands:
  descendants  List all of the descendant processes for a single process
```