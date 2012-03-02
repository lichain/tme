#!/bin/sh

/etc/init.d/tme-mistd start

ln -s /opt/trend/tme/conf/mist/tme-mistd.monit /etc/monit.d/tme-mistd.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

