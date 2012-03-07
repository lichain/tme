#!/bin/sh

rm -f /etc/monit.d/tme-graph-editor.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

/etc/init.d/tme-graph-editor stop
