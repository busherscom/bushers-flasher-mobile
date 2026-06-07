# Module cli

The `esptool-kt` command-line interface (built with Clikt). Wires the `core` protocol engine
and the `jvm-serial` transport into subcommands mirroring esptool.py: `write_flash`,
`read_flash`, `erase_flash`, `verify_flash`, `flash_id`, `chip_id`, `read_mac`, `image_info`,
`merge_bin`, the `espsecure`-style signing commands, and the safeguarded `burn_efuse`.

# Package io.github.ajsb85.esptoolkt.cli

Command definitions, global options, and the connection lifecycle helper used by every
device-facing subcommand.
