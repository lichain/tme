#!/bin/bash

rm -rf /tmp/tme-install
mkdir -p /tmp/tme-install/conf
cp ../conf/cluster.conf /tmp/tme-install/conf

cd ../utils
./longtime_kill.sh
cd ../bin

hosts=`../utils/getProperty.sh AllClients`
brokers=`../utils/getProperty.sh Brokers`
portals=`../utils/getProperty.sh Portal`

for host in $hosts
do
    echo copy to $host
    ssh -i ../conf/cluster.key $host "rm -rf /tmp/tme-install; mkdir /tmp/tme-install"
    scp -r -i ../conf/cluster.key ../* $host:/tmp/tme-install > /dev/null
done

for host in $hosts
do
    echo Remove tme-portal on $host
    ssh -i ../conf/cluster.key $host "rpm -e tme-portal"
done
for host in $hosts
do
    echo Remove tme-broker on $host
    ssh -i ../conf/cluster.key $host "rpm -e tme-broker"
done
for host in $hosts
do
    echo Remove tme-zookeeper on $host
    ssh -i ../conf/cluster.key $host "rpm -e tme-zookeeper"
done
for host in $hosts
do
    echo Remove tme-mist on $host
    ssh -i ../conf/cluster.key $host "rpm -e tme-mist"
done

for host in $hosts
do
    echo Clean-up $host
    ssh -i ../conf/cluster.key $host "/tmp/tme-install/utils/removeAll.sh"
done