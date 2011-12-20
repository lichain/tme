#!/bin/sh

rm -f /etc/monit.d/tme-broker.monit
/etc/init.d/monit reload

/etc/init.d/tme-broker stop
