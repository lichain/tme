#!/bin/sh -e

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/service tme-zookeeperd start
cp -f /usr/share/tme-zookeeper/tme-zookeeper.cron /etc/cron.d

exit 0
