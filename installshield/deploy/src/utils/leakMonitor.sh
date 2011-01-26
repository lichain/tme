#!/bin/bash
clear
clients=`./getProperty.sh AllClients`

while (true)
do
rm /tmp/tme-install/leakmon
echo `date` >> /tmp/tme-install/leakmon
for host in $clients
do
echo $host >>/tmp/tme-install/leakmon
echo `ssh -i ../conf/cluster.key $host /tmp/tme-install/utils/infoCollector.sh` >> /tmp/tme-install/leakmon
echo "" >> /tmp/tme-install/leakmon
done

clear
cat /tmp/tme-install/leakmon
sleep 5
done

