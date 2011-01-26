#!/bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

if [ $# -lt 1 ]; then
    echo "$0 mem_size{k,m,g}"
    exit 1
fi

zk_pkg=`rpm -qa | grep tme-zookeeper`
if [ "$zk_pkg" = "" ]; then
    echo tme-zookeeper is not installed
    exit 1
fi

heap_size=$1

service=tme-zookeeper
script=zkServer.sh
script_path=/usr/share/tme-zookeeper/bin/$script

echo "$service: change memory to $heap_size"

echo -e "edit $script_path"
cat $script_path | sed -e "s/JAVA_CMD=.*/JAVA_CMD=\"\/usr\/java\/latest\/bin\/java -Xmx$heap_size -Xms$heap_size\"/" > /tmp/$script
mv /tmp/$script $script_path; chmod 755 $script_path
echo "==> `grep JAVA_CMD= $script_path`"

echo done
echo remember to restart $service
