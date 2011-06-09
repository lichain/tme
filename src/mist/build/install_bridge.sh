#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

cp -f /usr/share/mist/etc/tme-bridge.cron /etc/cron.d
/sbin/service tme-bridge start

exit 0
