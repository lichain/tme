#!/bin/sh

/etc/init.d/tme-graph-editor start

ln -s /opt/trend/tme/conf/graph-editor/tme-graph-editor.monit /etc/monit.d/tme-graph-editor.monit

source /opt/trend/tme/bin/daemon.sh
reload_monit

