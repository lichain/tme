#!/bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

rm -f /etc/cron.d/tme-bridge.cron
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

/sbin/service tme-bridge stop
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi
/sbin/chkconfig --del tme-bridge
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

exit 0
