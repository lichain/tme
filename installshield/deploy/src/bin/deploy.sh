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
    ssh -i ../conf/cluster.key $host "/tmp/tme-install/utils/setupJava.sh"
done

tme_packages="tme-portal tme-broker tme-zookeeper tme-mist"
for package in $tme_packages; do
    for host in $hosts; do
        echo Remove $package on $host
        ssh -i ../conf/cluster.key $host "rpm -e $package"
    done
done

for host in $hosts
do
    echo Clean-up $host
    ssh -i ../conf/cluster.key $host "/tmp/tme-install/utils/removeAll.sh"
done

for host in $hosts
do
    echo Installing TME rpms on $host
    ssh -i ../conf/cluster.key $host "/tmp/tme-install/utils/install.sh $host"
done

zookeepers=`../utils/getProperty.sh Zookeepers`
i=1
for zk in $zookeepers
do
    echo Installing zookeeper on $zk
    ssh -i ../conf/cluster.key $zk "/tmp/tme-install/utils/install_zk.sh $i"
    i=$((i+1))
done

for host in $brokers
do
    echo Installing broker rpms on $host
    brokerHeap=`/tmp/tme-install/utils/getProperty.sh BrokerHeap`
    ssh -i ../conf/cluster.key $host "rpm -ivh /tmp/tme-install/rpm/tme-broker*.rpm"
    ssh -i ../conf/cluster.key $host "/usr/share/tme-broker/change_broker_mem.sh $brokerHeap"
    ssh -i ../conf/cluster.key $host "/etc/init.d/tme-brokerd restart"
    ssh -i ../conf/cluster.key $host "/usr/share/mist/bin/install_spyd.sh"
done

for host in $hosts
do
    echo Starting TME rpms on $host
    ssh -i ../conf/cluster.key $host "/usr/share/mist/bin/install_mistd.sh;"
done

for portal in $portals
do
    ssh -i ../conf/cluster.key $portal "/tmp/tme-install/utils/install_portal.sh $portal"
    break
done
