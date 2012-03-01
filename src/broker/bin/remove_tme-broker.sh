#!/bin/sh

rm -f /etc/monit.d/tme-broker.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

/etc/init.d/tme-broker stop

