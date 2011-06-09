#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/service tme-brokerd start

cp -f /usr/share/tme-broker/etc/tme-spyd.cron /etc/cron.d
/sbin/service tme-spyd start

exit 0
