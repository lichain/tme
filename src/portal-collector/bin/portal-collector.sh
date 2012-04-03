#!/bin/sh

source /opt/trend/tme/conf/common/common-env.sh

if [ "$1" == "daemon" ]
then
    $JAVA_CMD -cp '/opt/trend/tme/conf/portal-collector:/opt/trend/tme/lib/*' -Djmxtrans.log.level=ERROR com.trendmicro.tme.portal.ExchangeMetricCollector > /var/log/tme/portal-collector.err 2>&1 &
    echo $! > /var/run/tme/tme-portal-collector.pid
else
    $JAVA_CMD -cp '/opt/trend/tme/conf/portal-collector:/opt/trend/tme/lib/*' -Djmxtrans.log.level=ERROR com.trendmicro.tme.portal.ExchangeMetricCollector
fi
