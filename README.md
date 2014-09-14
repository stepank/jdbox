# jdbox

_jdbox_ is a simple Google Drive client for Linux. It is written in Java and uses `fuse-jna` to provide
a FUSE file system that can access files in Google Drive. It uses some caching to optimizes latency of some operations.

## Install, run, update

```
git clone https://github.com/stepank/jdbox.git
cd jdbox
make clean install
```

It will install _jdbox_ into `~/opt/jdbox`. It will also create mount point directory `~/mnt/jdbox` and
directory `~/.jdbox` for configs and credentials.

If you would like to change these defaults you can run this instead:

```
MOUNT_POINT=... INSTALL_DIR=... make clean install
```

To run _jdbox_:

```
$ INSTALL_DIR/jdbox.sh
```

To update runtime:

```
make clean update
```

To uninstall:

```
make uninstall
```

*WARNING:* do not run with `sudo`.

## Contacting me

Feel free to contact me concerning any problems, issues or questions about this
project via email [stepankk@gmail.com](mailto:stepankk@gmail.com).
