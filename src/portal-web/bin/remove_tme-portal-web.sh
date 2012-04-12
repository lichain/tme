#!/bin/bash

rm -f /etc/monit.d/tme-portal-web.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

/etc/init.d/tme-portal-web stop
