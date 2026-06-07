# Linux udev rules for ESP flashing hosts

`99-espressif-usb.rules` gives a non-root user reliable access to Espressif boards and the common
USB-UART bridges on a Linux flashing/manufacturing host, and stops ModemManager from interfering.

```bash
sudo cp 99-espressif-usb.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG plugdev "$USER"   # log out/in to take effect
```

Covered vendors: Espressif native USB `0x303A` (one rule covers all ~900 PIDs — S2-OTG, S3/C3/C5/C6/H2
USB-Serial-JTAG, P4, TinyUSB, devkit UF2/CircuitPython, community), Silicon Labs CP210x `0x10C4`,
WCH CH34x/CH9102 `0x1A86`, FTDI `0x0403`, Prolific `0x067B`.

## Other operating systems

- **macOS** — no rules needed; the CP210x/CH34x/FTDI vendor drivers (or the built-in CDC-ACM driver
  for native-USB ESP) expose `/dev/cu.usb*` automatically.
- **Windows** — native-USB ESP (USB-Serial-JTAG / S2 OTG) and modern CP210x/CH34x bind to the
  in-box `usbser.sys` CDC driver. For older bridges install the vendor driver (Silicon Labs / WCH /
  FTDI). For DFU/JTAG access with libusb tools, bind WinUSB via [Zadig](https://zadig.akeo.ie/).

> WSL note: attach the device with `usbipd attach`; the bridge then appears as `/dev/ttyUSB*` /
> `/dev/ttyACM*` inside WSL and these rules apply.
