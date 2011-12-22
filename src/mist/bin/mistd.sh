#!/bin/bash

CLASSPATH="/opt/trend/tme/conf/mist:/opt/trend/tme/lib/*"

if [ "$1" == "daemon" ]
then
    java -cp $CLASSPATH com.trendmicro.mist.Daemon > /var/log/tme/mistd.err 2>&1 &
    echo $! > /var/run/tme/tme-mistd.pid
else
    java -cp $CLASSPATH com.trendmicro.mist.Daemon
fi

