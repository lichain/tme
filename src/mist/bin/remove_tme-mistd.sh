#!/bin/sh

rm -f /etc/monit.d/tme-mistd.monit
/etc/init.d/monit reload

/etc/init.d/tme-mistd stop
