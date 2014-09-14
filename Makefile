SHELL = /bin/bash

clean:
	mvn clean
	rm -rf package

run:
	mvn exec:java -Dexec.mainClass="jdbox.JdBox" -Dexec.args="mnt"

package:
	mvn clean package dependency:copy-dependencies
	rm -rf package
	mkdir -p package/lib
	cp target/*.jar package/lib
	cp install/jdbox.sh package/

INSTALL_DIR ?= $(HOME)/opt/jdbox

install_runtime: package
	rm -rf $(INSTALL_DIR)
	mkdir -p $(INSTALL_DIR)
	cp -r package/* $(INSTALL_DIR)
	cp install/jdbox.desktop $(INSTALL_DIR)
	sed -i 's#\$$INSTALL_DIR#$(INSTALL_DIR)#' $(INSTALL_DIR)/jdbox.desktop

MOUNT_POINT ?= $(HOME)/mnt/jdbox

install: install_runtime
	mkdir -p ~/.jdbox
	echo $(INSTALL_DIR) > ~/.jdbox/install_dir
	cp install/config $(HOME)/.jdbox
	sed -i 's#\$$MOUNT_POINT#$(MOUNT_POINT)#' ~/.jdbox/config
	mkdir -p $(MOUNT_POINT)

update:
	test -f ~/.jdbox/install_dir
	INSTALL_DIR=$$(cat ~/.jdbox/install_dir) make install_runtime

uninstall:
	test -f ~/.jdbox/install_dir
	rm -rf $$(cat ~/.jdbox/install_dir)
	rm -rf ~/.jdbox
