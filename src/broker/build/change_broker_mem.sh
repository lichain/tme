#bin/sh

if [ `whoami` != "root" ]; then 
    echo requires root permission
    exit 1;
fi

if [ $# -lt 1 ]; then
    echo "$0 mem_size{k,m,g}"
    exit 1
fi

broker_pkg=`rpm -qa | grep tme-broker`
if [ "$broker_pkg" = "" ]; then
    echo tme-broker is not installed
    exit 1
fi

service=tme-broker
script="/etc/init.d/tme-brokerd"
config="/usr/share/tme-broker/mq/var/instances/imqbroker/props/config.properties"

brokerHeap=$1

echo "$service: change memory to $brokerHeap"

echo -e "edit $script"
sed -e "s/-Xmx[0-9]*[kmg] -Xms[0-9]*[kmg]/-Xmx$brokerHeap -Xms$brokerHeap/g" $script > /tmp/tme-brokerd
mv -f /tmp/tme-brokerd $script; chmod 755 $script
echo "==> `grep jvm_mem= $script`"

echo -e "edit $config"
sed -e '/imq.system.max_size=/d' $config > /tmp/config.properties
echo imq.system.max_size=$brokerHeap >> /tmp/config.properties
chown TME:TME /tmp/config.properties
mv -f /tmp/config.properties $config
echo "==> `tail -1 $config`"

echo done
echo remember to restart $service
