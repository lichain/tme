#!/bin/sh

/etc/init.d/tme-broker start
ln -s /opt/trend/tme/conf/broker/tme-broker.monit /etc/monit.d/tme-broker.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

