#!/bin/bash
sed -e "/$1=/!d" -e "s/$1=//g" /tmp/tme-install/conf/cluster.conf
