#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

cp -f /usr/share/mist/etc/tme-mist.cron /etc/cron.d
/sbin/service mistd start

exit 0;
