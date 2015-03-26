# jdbox

_jdbox_ is a simple Google Drive client for Linux. It is written in Java and uses `fuse-jna` to provide
a FUSE file system that can access files in Google Drive. It uses some caching to optimize latency of some operations.

## Install, run, update

```
git clone https://github.com/stepank/jdbox.git
cd jdbox
git submodule init
git submodule update
make clean install
```

*NOTE:* make sure that JDK7 is installed.

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

## Development

The easiest way to set up development environment is to run:

```
make setup_tests
```

It will create mount point `~/mnt/jdbox-test` and config directory `~/.jdbox-test` to set up test
environment. To do so, it will ask you to open a link in your web browser to generate an OAuth 2.0
key that will be used to authorize all requests to Google Drive API performed during auto tests.

*WARNING:* I suggest not using your personal Google account, but to create a test one - these are
only tests, after all.

After doing this set up, you will be able to run auto tests with:

```
make test
```

In fact, this target depends on `setup_tests`, but I decided to be more explicit here.

## Contacting me

Feel free to contact me concerning any problems, issues or questions about this
project via email [stepankk@gmail.com](mailto:stepankk@gmail.com).
