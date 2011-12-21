#!/bin/bash

MEMORY=`sed -e '/imq\.system\.max_size/!d ; s/.*=//g' /opt/trend/tme/conf/broker/config.properties`

CLASSPATH="/opt/trend/tme/conf/broker:/opt/trend/tme/lib/*"
JVM_ARGS="-server -Xmx$MEMORY -Xms$MEMORY -Dcom.sun.management.jmxremote.port=5566 -Dcom.sun.management.jmxremote.authenticate=true -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.password.file=/opt/trend/tme/conf/broker/jmxremote.password -Dcom.sun.management.jmxremote.access.file=/opt/trend/tme/conf/broker/jmxremote.access"

if [ "$1" == "daemon" ]
then
    java -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.broker.EmbeddedOpenMQ > /var/log/tme/broker.err 2>&1 &
    echo $! > /var/run/tme/tme-broker.pid
else
    java -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.broker.EmbeddedOpenMQ
fi

