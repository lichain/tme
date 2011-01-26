#!/bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

/sbin/chkconfig --add tme-brokerd
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi
/sbin/service tme-brokerd start
if [ $? != 0 ]; then echo "$0: error at line $LINENO"; exit $?; fi

exit 0
