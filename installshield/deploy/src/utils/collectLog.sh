#!/bin/bash

dateStr=`date +%F_%H_%M_%S`
clients=`./getProperty.sh AllClients`

for host in $clients
do
mkdir -p /tmp/tme-install/log-$dateStr/$host
ssh -i ../conf/cluster.key $host "/tmp/tme-install/utils/packLog.sh"
scp -i ../conf/cluster.key $host:/tmp/tme-install/tmelog/* /tmp/tme-install/log-$dateStr/$host
done

./longtime_watch.sh > /tmp/tme-install/log-$dateStr/longtime.all
rpm -qa|grep tme > /tmp/tme-install/log-$dateStr/tme-version
cd /tmp/tme-install
tar -zcvf log-$dateStr.tar.gz log-$dateStr/
cp log-$dateStr.tar.gz ~/
