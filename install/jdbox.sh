#!/bin/bash

LIB=$(dirname "${BASH_SOURCE[0]}")/lib

java -cp "$LIB/*" jdbox.JdBox $1
