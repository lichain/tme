#!/bin/bash

source /opt/trend/tme/conf/common/common-env.sh

CLASSPATH="/opt/trend/tme/conf/graph-editor:/opt/trend/tme/lib/*:/opt/trend/tme/lib/jersey/*:/opt/trend/tme/lib/jetty/*"
JVM_ARGS="-Djava.security.auth.login.config=/opt/trend/tme/conf/graph-editor/ldaploginmodule.conf -Djava.security.egd=file:/dev/./urandom"

rm -rf /var/lib/tme/graph-editor/jsp
mkdir -p /var/lib/tme/graph-editor/jsp

if [ "$1" == "daemon" ]
then
    $JAVA_CMD -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.grapheditor.GraphEditorMain > /var/log/tme/graph-editor.err 2>&1 &
    echo $! > /var/run/tme/tme-graph-editor.pid
else
    $JAVA_CMD -cp $CLASSPATH $JVM_ARGS com.trendmicro.tme.grapheditor.GraphEditorMain
fi

