#!/bin/sh

/etc/init.d/tme-portal-collector start

ln -s /opt/trend/tme/conf/portal-collector/tme-portal-collector.monit /etc/monit.d/tme-portal-collector.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

