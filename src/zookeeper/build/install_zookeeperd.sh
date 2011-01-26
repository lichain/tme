#!/bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/chkconfig --add tme-zookeeperd
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi
/sbin/service tme-zookeeperd start
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

cp -f /usr/share/tme-zookeeper/tme-zookeeper.cron /etc/cron.d
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

exit 0
