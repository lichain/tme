#!/bin/bash

rm -rf /tmp/tme-install/tmelog
rm -rf /tmp/tme-install/tmelog.tar.gz
cp -rf /var/run/tme /tmp/tme-install/tmelog
rm -f /tmp/tme-install/tmelog/*.pid

cp /usr/share/tme-broker/var/mq/instances/imqbroker/log/log.txt /tmp/tme-install/tmelog/broker.log
