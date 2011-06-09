#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

rm -f /etc/cron.d/tme-zookeeper.cron
/sbin/service tme-zookeeperd stop

exit 0
