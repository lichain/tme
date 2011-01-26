#!/bin/bash

rpm -ivh /tmp/tme-install/rpm/tme-zookeeper*.rpm

zkHosts=`/tmp/tme-install/utils/getProperty.sh Zookeepers`

if [[ "`echo $zkHosts|wc -w`" != 1 ]]; then
    i=1
    for zkHost in $zkHosts
    do
        echo server.$i=$zkHost:2888:3888 >> /usr/share/tme-zookeeper/conf/zoo.cfg
        i=$((i+1))
    done

    echo $1 > /var/tme-zookeeper/myid
fi

/usr/share/tme-zookeeper/install_zookeeperd.sh