#!/bin/sh

/etc/init.d/tme-portal-web start

ln -s /opt/trend/tme/conf/portal-web/tme-portal-web.monit /etc/monit.d/tme-portal-web.monit
/etc/init.d/monit reload

