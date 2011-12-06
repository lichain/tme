#!/bin/sh

rm -f /etc/monit.d/tme-portal-collector.monit
/etc/init.d/monit reload

/etc/init.d/tme-portal-collector stop
