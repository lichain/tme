#!/bin/bash

msglimit=100000
if [[ "" != "$1" ]]; then
    msglimit=$1
fi

clients=`./getProperty.sh TestClients`
for host in $clients
do
    ssh -i ../conf/cluster.key $host "cd /tmp/tme-install/QA/mist ; ./t_longtime $msglimit >/tmp/tme-install/longtime.out &"
sleep 1
done
