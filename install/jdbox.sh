#!/bin/bash

LIB=$(cat ~/.jdbox/install_dir)/lib

java -cp "$LIB/*" jdbox.JdBox $1
