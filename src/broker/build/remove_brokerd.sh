#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

rm -f /etc/cron.d/tme-spyd.cron
/sbin/service tme-spyd stop

/sbin/service tme-brokerd stop

exit 0
