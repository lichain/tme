#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

rm -f /etc/cron.d/tme-mist.cron
/sbin/service mistd stop

exit 0
