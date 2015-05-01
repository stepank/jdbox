SHELL = /bin/bash

clean:
	mvn clean
	rm -rf package

run:
	mvn compile exec:java -Dexec.mainClass="jdbox.JdBox"

DATA_DIR_SUFFIX ?= .jdbox
DATA_DIR = $(HOME)/$(DATA_DIR_SUFFIX)
MOUNT_POINT ?= $(HOME)/mnt/jdbox

install_home:
	mkdir -p $(DATA_DIR)/storage
	cp install/config $(DATA_DIR)
	sed -i 's#\$$MOUNT_POINT#$(MOUNT_POINT)#' $(DATA_DIR)/config
	mkdir -p $(MOUNT_POINT)

setup_tests:
	DATA_DIR_SUFFIX=.jdbox-test MOUNT_POINT=$(HOME)/mnt/jdbox-test make install_home
	mvn clean compile exec:java -Dexec.mainClass="jdbox.SetUpTests"

test: setup_tests
	mvn test

INSTALL_DIR ?= $(HOME)/opt/jdbox

package:
	mvn clean package -DskipTests dependency:copy-dependencies
	rm -rf package
	mkdir -p package/lib
	# copy libs
	cp target/*.jar package/lib
	# copy executable & shortcut
	cp install/jdbox.sh package/
	cp install/jdbox.desktop package/
	# filter executable & shortcut
	sed -i 's#\$$INSTALL_DIR#$(INSTALL_DIR)#' package/jdbox.sh package/jdbox.desktop

install: package install_home
	echo $(INSTALL_DIR) > $(DATA_DIR)/install_dir
	rm -rf $(INSTALL_DIR)
	mkdir -p $(INSTALL_DIR)
	cp -r package/* $(INSTALL_DIR)

update:
	test -f $(DATA_DIR)/install_dir
	INSTALL_DIR=$$(cat $(DATA_DIR)/install_dir) make install

uninstall:
	test -f (DATA_DIR)/install_dir
	rm -rf $$(cat $(DATA_DIR)/install_dir)
	rm -rf $(DATA_DIR)
