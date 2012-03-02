#!/bin/sh

rm -f /etc/monit.d/tme-portal-web.monit
/etc/init.d/monit reload

/etc/init.d/tme-portal-web stop
