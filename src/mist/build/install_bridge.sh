#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/service tme-bridge start
cp -f /usr/share/mist/etc/tme-bridge.cron /etc/cron.d

exit 0
