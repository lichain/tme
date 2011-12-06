#!/bin/bash

CLASSPATH="/opt/trend/tme/conf/graph-editor:/opt/trend/tme/lib/*:/opt/trend/tme/lib/jersey/*:/opt/trend/tme/lib/jetty/*"
JVM_ARGS="-Djava.library.path=/usr/lib64/graphviz/java/ -Djava.security.egd=file:/dev/./urandom"


if [ "$1" == "daemon" ]
then
    java -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.grapheditor.GraphEditor > /var/log/tme/graph-editor.err 2>&1 &
    echo $! > /var/run/tme/tme-graph-editor.pid
else
    java -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.grapheditor.GraphEditor
fi

