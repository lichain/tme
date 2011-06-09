#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

rm -f /etc/cron.d/tme-bridge.cron
/sbin/service tme-bridge stop

exit 0
