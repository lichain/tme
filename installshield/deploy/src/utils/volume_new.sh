#!/bin/bash

#TODO: seperate to hosts {exg num}
#TODO: substract time of waiting msg stop recieving

if [ $# -lt 10 ]; then
    echo "usage: $0 [1.exchange_name_prefix] [2.exchange_num] [3.msg_size] [4.total_msg_count] [5.total_producers]"
    echo "[6.total_consumers] [7.exgs_per_consumer] [8.interval] [9.sink_type] [10.interface]"
    echo ""
	echo "    * [1.exchange_name_prefix] = if topic:xxx, then it's set to topic exchange"
    echo "    * [5.total_producers] MUST >= total hosts && [5.total_producers] / (total hosts) >= [2.exchange_num]"
    echo "    * [6.total_consumers] MUST >= total hosts"
    echo "    * if [7.exgs_per_consumer] > 1, it must apply [2.exchange_num] = ( [6.total_consumers] / [host num] ) * [7.exgs_per_consumer]"
    echo "    * [9.sink_type]= 0:normal | 1:recreate session | 2:reattach session"
	echo "    * [10.interface]=toolkit:mist toolkit | socket:mist socket API"
    echo "    * hosts that will serve as consumer/producer are defined in cluster.conf -> TestClients="
	echo "    * inside script, recv_stop_ttl : ttl(sec) to determine if consumer stop receiving msg and terminate"
    echo ""
	echo "    * note: Currently, each host will transmit on all exchange numbers"
	echo "    * it will make sure all consumer are attached to session before producer start, so should no msg loss from topic"
	echo "    * session MUST be clean before each start"
	echo "    * please save log file before each run since it will clear the env"
	echo "    * (consumer/producer number on each host) should be multiple times than exchange number"
	echo ""
    exit 1
fi

rm -f /tmp/tme-install/volume.*

# config
recv_stop_ttl=10
one_msg_md5_size=33
# /config

EXCHANGE_NAME_PREFIX=$1
EXCHANGE_NUM=$2
MSG_SIZE=$3
TOTAL_MSG_COUNT=$4
HOSTS=`./getProperty.sh TestClients`
HOST_COUNT=`./getProperty.sh TestClients|wc -w`
PRODUCER=$5
CONSUMER=$6
EXGS_PER_CONSUMER=$7
INTERVAL=$8
SINK_TYPE=$9
INTERFACE=${10}
# determine if it's topic
if [[ $EXCHANGE_NAME_PREFIX == topic:* ]]; then
	IS_TOPIC=1
else
	IS_TOPIC=0
fi

echo "DEBUG: INTERFACE: $INTERFACE"

if [ $INTERFACE == "toolkit" ]; then
	SOURCE_CMD=t_multi_source_new
	SOURCE_OUT=t_multi_source
	SINK_CMD=t_multi_sink_new
	SINK_OUT=t_multi_sink
else
	SOURCE_CMD=t_socket_source_new
	SOURCE_OUT=t_socket_source
	SINK_CMD=t_socket_sink_new
	SINK_OUT=t_socket_sink
fi

# Error handing
if (( $PRODUCER / $HOST_COUNT < $EXCHANGE_NUM )); then
	echo "producer per hosts must >= exchange num"
	exit 1
fi
if (( $PRODUCER < $HOST_COUNT )); then
	echo "producer must >= host num"
	exit 1
fi
if (( $CONSUMER < $HOST_COUNT )); then
	echo "consumer must >= host num"
	exit 1
fi
if (( $EXGS_PER_CONSUMER > 1 )) || (( $EXCHANGE_NUM > ( $CONSUMER / $HOST_COUNT ) )); then
	if (( $EXCHANGE_NUM != ( $CONSUMER / $HOST_COUNT ) * $EXGS_PER_CONSUMER )); then
		echo "[2.exchange_num] = ( [6.total_consumers] / [host num] ) * [7.exgs_per_consumer]"
		exit 1
	fi
fi
clearup() {
	echo -n "cleaning up..."
	for HOST in $HOSTS
	do
		ssh -i ../conf/cluster.key $HOST /tmp/tme-install/utils/destroy_all.sh $EXCHANGE_NAME_PREFIX > /dev/null 2>&1
	done
	rm -f /tmp/tme-install/volume.*
	echo "done"
}

clearup

#generate Exchange name list
EXCHANGE_NAMES=""
for((i=1;i<=$EXCHANGE_NUM;i++))
do
    EXCHANGE_NAME=$EXCHANGE_NAME_PREFIX$i
    EXCHANGE_NAMES="$EXCHANGE_NAMES $EXCHANGE_NAME"
done
echo "DEBUG: exchange name list: $EXCHANGE_NAMES"

start_ts=`date +%s`

# initiate all consumers among all hosts
CONSUMER_PER_HOST=0
HOSTi=1
for HOST in $HOSTS
do
	echo "DEBUG: for $HOST..."
    # for first host, the msg/producer/consumer count will be the divided + remainder
    if [ $HOSTi -eq 1 ]; then
        (( CONSUMER_PER_HOST = (CONSUMER / HOST_COUNT) + (CONSUMER % HOST_COUNT) ))        
    else
        (( CONSUMER_PER_HOST = CONSUMER / HOST_COUNT ))
    fi
    # generate 1 liner of exchange names per consumer and feed into consumer command
    m=1
    EXCHANGE_NAMES_PER_CONSUMER=""
    for EXCHANGE_NAME in $EXCHANGE_NAMES
    do
        EXCHANGE_NAMES_PER_CONSUMER="$EXCHANGE_NAMES_PER_CONSUMER $EXCHANGE_NAME"
        ((margin=$m % $EXGS_PER_CONSUMER))
        if [ $margin -eq 0 ]; then
            # if EXGS_PER_CONSUMER more than 1, then each consumer mount to N exg, else all consumer mount to the same exg
            if [ $EXCHANGE_NUM -ge $CONSUMER_PER_HOST ]; then
                CONSUMER_PER_SESSION=1
            else
				if [ $m -eq $EXGS_PER_CONSUMER ]; then
				(( CONSUMER_PER_SESSION = ( CONSUMER_PER_HOST / EXCHANGE_NUM) + CONSUMER_PER_HOST % EXCHANGE_NUM )) 
				else
                (( CONSUMER_PER_SESSION = CONSUMER_PER_HOST / EXCHANGE_NUM ))
				fi
            fi
            echo "DEBUG: for exchange: $EXCHANGE_NAMES_PER_CONSUMER, $CONSUMER_PER_SESSION of consumer(s) are receiving..."
            ssh -i ../conf/cluster.key $HOST "cd /tmp/tme-install ; nice -n 10 /tmp/tme-install/QA/mist/$SOURCE_CMD $CONSUMER_PER_SESSION $EXCHANGE_NAMES_PER_CONSUMER" >> /tmp/tme-install/v.source.log 2>&1 & 
            # if no sleep, the ssh -i will probably failed
            sleep 0.5
            EXCHANGE_NAMES_PER_CONSUMER=""
        fi 
        ((m=m+1))
    done
    ((HOSTi=HOSTi+1))
done

# checking if all consumer are initialized
echo -n "checking if all consumers are initialized..."

EXCHANGE_REGEX=""
for((i2=1;i2<=$EXGS_PER_CONSUMER;i2++))
do 
	EXCHANGE_REGEX=${EXCHANGE_REGEX}.*?${EXCHANGE_NAME_PREFIX}
done
total_initialized_consumers=0
while [[ $total_initialized_consumers -ne $CONSUMER ]]
do
	total_initialized_consumers=0
	for HOST in $HOSTS
	do
		#echo "DEBUG: EXCHANGE_REGEX: $EXCHANGE_REGEX"
		ssh -i ../conf/cluster.key $HOST "mist-session -l|grep -P BUSY.*?consumer.*?${EXCHANGE_REGEX} | wc -l" > /tmp/tme-install/tmp.session_num.$HOST
		sleep 0.5
		this_host_initialized_consumers=`cat /tmp/tme-install/tmp.session_num.$HOST`
		(( total_initialized_consumers = total_initialized_consumers + this_host_initialized_consumers ))
	done
	#echo "DEBUG: total_initialized_consumers = $total_initialized_consumers"
	sleep 0.5
done
echo "YES"
rm -f /tmp/tme-install/tmp*

start_producer_ts=`date +%s`

# initiate all producers among all hosts and distribute msg to each producer in each host
MSG_COUNT_PER_HOST=0
PRODUCER_PER_HOST=0
HOSTi=1
for HOST in $HOSTS
do
    # for first host, the msg/producer/consumer count will be the divided + remainder
    if [ $HOSTi -eq 1 ]; then
        (( MSG_COUNT_PER_HOST = (TOTAL_MSG_COUNT / HOST_COUNT) + (TOTAL_MSG_COUNT % HOST_COUNT) ))
        (( PRODUCER_PER_HOST = (PRODUCER / HOST_COUNT) + (PRODUCER % HOST_COUNT) ))      
    else
        (( MSG_COUNT_PER_HOST = TOTAL_MSG_COUNT / HOST_COUNT ))
        (( PRODUCER_PER_HOST = PRODUCER / HOST_COUNT ))
    fi
    # exgs will be distributed to total producers
	echo "DEBUG: There are $MSG_COUNT_PER_HOST msgs to be sent by $PRODUCER_PER_HOST Producers at host:$HOST"
    MSG_COUNT_PER_EXG=0
    k=1
    for EXCHANGE_NAME in $EXCHANGE_NAMES
    do
    # for first exg, it get divided msg count + remainder
    first=0
        if [ "$EXCHANGE_NAME" == "$EXCHANGE_NAME_PREFIX$first" ]; then
            (( MSG_COUNT_PER_EXG =(MSG_COUNT_PER_HOST / EXCHANGE_NUM) + (MSG_COUNT_PER_HOST % EXCHANGE_NUM) ))
        else
            (( MSG_COUNT_PER_EXG = MSG_COUNT_PER_HOST / EXCHANGE_NUM ))
        fi
        (( PRODUCER_PER_EXG = PRODUCER_PER_HOST / EXCHANGE_NUM ))
		echo "DEBUG: job $k : $PRODUCER_PER_EXG producers will send $MSG_COUNT_PER_EXG msgs for exchange: $EXCHANGE_NAME"
        ssh -i ../conf/cluster.key $HOST "cd /tmp/tme-install ; nice -n 10 /tmp/tme-install/QA/mist/$SINK_CMD $MSG_SIZE $MSG_COUNT_PER_EXG $PRODUCER_PER_EXG $EXCHANGE_NAME $INTERVAL $SINK_TYPE" >> /tmp/tme-install/v.sink.log 2>&1 &
        # if no sleep, the ssh -i will probably failed
        sleep 0.5
        ((k=k+1))
    done
    ((HOSTi=HOSTi+1))
done

total=0
if [ $IS_TOPIC -eq 1 ]; then
	# if topic, consumer will get N times msg from producers sent, N = consumer # / exg #
	(( N_TIMES_MSG_C_OVER_P = CONSUMER / EXCHANGE_NUM ))
	if [ $N_TIMES_MSG_C_OVER_P -le 1 ]; then
		N_TIMES_MSG_C_OVER_P=$HOST_COUNT
	fi
	(( TOTAL_MSG_COUNT_FOR_COMPARE = TOTAL_MSG_COUNT * N_TIMES_MSG_C_OVER_P ))
else
	TOTAL_MSG_COUNT_FOR_COMPARE=$TOTAL_MSG_COUNT
	N_TIMES_MSG_C_OVER_P=1
fi

# wait till recieved msg = expected sent, or recieve msg amount stop growing for ttl time
total_recv=0
total_previous=0
total_same_count=0
check_start_ts=0
check_end_ts=0

while([ $total_recv != $TOTAL_MSG_COUNT_FOR_COMPARE ])
do
    total_recv=0
	total_sent=0
    for HOST in $HOSTS
    do
        (( cnt_recv_this_host = `ssh -i ../conf/cluster.key $HOST stat -c %s /tmp/tme-install/$SOURCE_OUT.*.out.* | awk '{s+=$1} END {print s}'` / one_msg_md5_size ))
        ((total_recv=total_recv+cnt_recv_this_host))
		(( cnt_sent_this_host = `ssh -i ../conf/cluster.key $HOST stat -c %s /tmp/tme-install/$SINK_OUT.*.out.* | awk '{s+=$1} END {print s}'` / one_msg_md5_size ))
		((total_sent=total_sent+cnt_sent_this_host))
    done
    echo "total received is $total_recv ; total sent is $total_sent ; expected total received: $TOTAL_MSG_COUNT_FOR_COMPARE"
	if [ $total_recv -ne 0 ] && [ $total_previous -eq $total_recv ]; then
		(( total_same_count = total_same_count + 1 ))
	else
		total_same_count=0
		check_start_ts=`date +%s`
	fi
	total_previous=$total_recv
	if [ $total_same_count -ge $recv_stop_ttl ]; then
		echo "consumer seems stop recieving...so terminate program ! consumer should get total $TOTAL_MSG_COUNT_FOR_COMPARE msgs "
		check_end_ts=`date +%s`
		clearup
		exit 1
	fi
    sleep 1
done
end_ts=`date +%s`
# ((check_period=check_end_ts-check_start_ts))
((total_ts=end_ts-start_producer_ts))
((total_size=TOTAL_MSG_COUNT*MSG_SIZE))
((total_msg_count_recv=TOTAL_MSG_COUNT*N_TIMES_MSG_C_OVER_P))
((total_msg_size_recv=total_msg_count_recv*MSG_SIZE))

echo "throughput(msg amount) for msg sent: `expr $TOTAL_MSG_COUNT / $total_ts` msgs / sec"
echo "throughput(size) for msg sent: `expr $total_size / $total_ts` KBytes / sec"
echo "throughput(msg amount) for msg received: `expr $total_msg_count_recv / $total_ts` msgs / sec"
echo "throughput(size) for msg received: `expr $total_msg_size_recv / $total_ts` KBytes / sec"
echo "time elapse: $total_ts secs"
echo "( note: if topic, consumers will be distributed by $N_TIMES_MSG_C_OVER_P times of msg)"

echo -n Verifying result...

for HOST in $HOSTS
do
    ssh -i ../conf/cluster.key $HOST cat /tmp/tme-install/$SOURCE_OUT.*.out.* >> /tmp/tme-install/volume.source.$EXCHANGE_NAME_PREFIX.all
    ssh -i ../conf/cluster.key $HOST cat /tmp/tme-install/$SINK_OUT.*.out.* >> /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all
done

if [ $IS_TOPIC -eq 1 ]; then
	echo -n "DEBUG: comparing topic msg..."
	# repeat sink volume N times
	for ((o=1;o<=$N_TIMES_MSG_C_OVER_P;o++))
	do
		cat /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all >> /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all.tmp
	done
	mv -f /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all.tmp /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all
fi

sort /tmp/tme-install/volume.source.$EXCHANGE_NAME_PREFIX.all > /tmp/tme-install/volume.source.$EXCHANGE_NAME_PREFIX.all.sorted
sort /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all > /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all.sorted
diff /tmp/tme-install/volume.sink.$EXCHANGE_NAME_PREFIX.all.sorted /tmp/tme-install/volume.source.$EXCHANGE_NAME_PREFIX.all.sorted

if [ $? == 0 ]; then
    echo SUCCESS
    result=0
else
    echo FAILED
    result=1
fi

clearup

if [ $result == 0 ]; then
    exit 0
else
    exit 1
fi