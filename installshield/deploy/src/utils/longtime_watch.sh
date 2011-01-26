#!/bin/bash

clients=`./getProperty.sh TestClients`
for host in $clients
do
    echo $host
    ssh -i ../conf/cluster.key $host cat /tmp/tme-install/longtime.out
    echo ""
done
