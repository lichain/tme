#!/bin/sh

rm -f /etc/monit.d/tme-graph-editor.monit
/etc/init.d/monit reload

/etc/init.d/tme-graph-editor stop
