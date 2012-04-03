#!/bin/bash

source /opt/trend/tme/conf/common/common-env.sh

CLASSPATH="/opt/trend/tme/conf/mist:/opt/trend/tme/lib/*"

if [ "$1" == "daemon" ]
then
    $JAVA_CMD -cp $CLASSPATH com.trendmicro.mist.Daemon > /var/log/tme/mistd.err 2>&1 &
    echo $! > /var/run/tme/tme-mistd.pid
else
    $JAVA_CMD -cp $CLASSPATH com.trendmicro.mist.Daemon
fi

