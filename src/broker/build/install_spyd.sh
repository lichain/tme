#!/bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/chkconfig --add tme-spyd
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi
/sbin/service tme-spyd start
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

cp -f /usr/share/tme-broker/etc/tme-spyd.cron /etc/cron.d
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

exit 0
