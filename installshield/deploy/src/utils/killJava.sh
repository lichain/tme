#!/bin/bash

pid=`/usr/java/latest/bin/jps|sed -e "/$1/!d" -e "s/$1//g"`
if [[ "$pid" != "" ]]; then
    kill -9 `/usr/java/latest/bin/jps|sed -e "/$1/!d" -e "s/$1//g"`
fi
