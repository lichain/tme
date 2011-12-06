#!/bin/sh

/etc/init.d/tme-graph-editor start

ln -s /opt/trend/tme/conf/graph-editor/tme-graph-editor.monit /etc/monit.d/tme-graph-editor.monit
/etc/init.d/monit reload

