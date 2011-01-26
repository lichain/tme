#!/bin/bash

rpm -q jdk
if [ "$?" == "1" ]; then
    rpm -ivh /tmp/tme-install/rpm/jdk*.rpm
    rpm -ivh /tmp/tme-install/rpm/sun*.rpm
fi
