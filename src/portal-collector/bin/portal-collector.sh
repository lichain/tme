#!/bin/sh

if [ "$1" == "daemon" ]
then
    java -cp '/opt/trend/tme/conf/portal-collector:/opt/trend/tme/lib/*' -Djmxtrans.log.level=ERROR com.trendmicro.tme.portal.ExchangeMetricCollector > /var/log/tme/portal-collector.err 2>&1 &
    echo $! > /var/run/tme/tme-portal-collector.pid
else
    java -cp '/opt/trend/tme/conf/portal-collector:/opt/trend/tme/lib/*' -Djmxtrans.log.level=ERROR com.trendmicro.tme.portal.ExchangeMetricCollector
fi
