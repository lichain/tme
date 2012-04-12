#!/bin/bash

rm -f /etc/monit.d/tme-mistd.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

/etc/init.d/tme-mistd stop
