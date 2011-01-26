#!/bin/bash

clients=`./getProperty.sh AllClients`
for host in $clients
do
    ssh -i ../conf/cluster.key $host "killall t_multi; killall t_longtime; killall t_socket"
done
