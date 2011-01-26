#!/bin/sh

if [ $# -lt 6 ]; then
    echo "usage: $0 exchange_name msg_size total_msg_count producers_per_host consumers_per_host interval"
    exit 0
fi

rm -f /tmp/tme-install/volume.*

EXCHANGE_NAME=$1
MSG_SIZE=$2
TOTAL_MSG_COUNT=$3
HOSTS=`./getProperty.sh TestClients`
HOST_COUNT=`./getProperty.sh TestClients|wc -w`
PRODUCER=$4
CONSUMER=$5
INTERVAL=$6

start_ts=`date +%s`

i=0
for HOST in $HOSTS
do
    if [ $i ==  $((HOST_COUNT-1)) ]; then
        MSG_COUNT=`expr $TOTAL_MSG_COUNT / $HOST_COUNT`
        MSG_COUNT=`expr $MSG_COUNT + $TOTAL_MSG_COUNT - $MSG_COUNT \* $HOST_COUNT`
    else
        MSG_COUNT=`expr $TOTAL_MSG_COUNT / $HOST_COUNT`
    fi
    ssh -i ../conf/cluster.key $HOST "cd /tmp/tme-install ; nice -n 10 /tmp/tme-install/QA/mist/t_socket_sink $MSG_SIZE $MSG_COUNT $PRODUCER $EXCHANGE_NAME $INTERVAL" &
    ssh -i ../conf/cluster.key $HOST "cd /tmp/tme-install ; nice -n 10 /tmp/tme-install/QA/mist/t_socket_source $CONSUMER $EXCHANGE_NAME" &
    ((i=i+1))
done

total=0
while([ $total != $TOTAL_MSG_COUNT ])
do
    total=0
    for HOST in $HOSTS  
    do
        cnt=`ssh -i ../conf/cluster.key $HOST cat /tmp/tme-install/t_socket_source.out.* | wc -l`
        ((total=total+cnt))
    done
    echo total is $total
    sleep 1
done

end_ts=`date +%s`
((total_ts=end_ts-start_ts))

echo throughput is: `expr $TOTAL_MSG_COUNT / $total_ts` msgs / sec

echo -n Verifying result...

for HOST in $HOSTS  
do
    ssh -i ../conf/cluster.key $HOST cat /tmp/tme-install/t_socket_source.out.* >> /tmp/tme-install/volume.source.all
    ssh -i ../conf/cluster.key $HOST cat /tmp/tme-install/t_socket_sink.out.* >> /tmp/tme-install/volume.sink.all
done

for HOST in $HOSTS  
do
    ssh -i ../conf/cluster.key $HOST /tmp/tme-install/utils/destroy_all.sh $EXCHANGE_NAME > /dev/null
done

sort /tmp/tme-install/volume.source.all > /tmp/tme-install/volume.source.all.sorted
sort /tmp/tme-install/volume.sink.all > /tmp/tme-install/volume.sink.all.sorted
diff /tmp/tme-install/volume.sink.all.sorted /tmp/tme-install/volume.source.all.sorted

if [ $? == 0 ]; then
    echo SUCCESS
    rm -f /tmp/tme-install/volume.*
    exit 0
else
    echo FAILED
    rm -f /tmp/tme-install/volume.*
    exit 1
fi