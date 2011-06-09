#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/service mistd start
cp -f /usr/share/mist/etc/tme-mist.cron /etc/cron.d

exit 0;
