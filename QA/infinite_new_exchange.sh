#!/bin/sh

size=1000
max_batch_count=100
test_exchange="test_`uuidgen | tr - _`"
checksum_fifo="/tmp/$test_exchange.fifo"

rm -f $checksum_fifo
mkfifo $checksum_fifo

###################################

test_counter=0
run=1
retval=0
trap "export run=0" INT

while [ $run == 1 ]
do
    test_counter=$((test_counter+1))
    batch_count=$((RANDOM % max_batch_count + 1))
    echo -n "Test #$test_counter: Delivering $batch_count messages ..."

    (cat $checksum_fifo | md5sum > /tmp/$test_exchange.md5sum) &
    
    sink_sid=`mist-session`
    (for((i=0;i<$batch_count;i++)); do dd if=/dev/urandom bs=$size count=1 2>/dev/null | base64 -w 0 ; echo "" ; done) | tee $checksum_fifo | mist-encode -w $test_exchange -l | mist-sink $sink_sid -a &

    src_sid=`mist-session`
    mist-source $src_sid -m $test_exchange 2>/dev/null
    src_md5=`mist-source $src_sid -a -l $batch_count | mist-decode -l | md5sum`

    mist-session -d $sink_sid 2>/dev/null
    mist-session -d $src_sid 2>/dev/null

    sink_md5=`cat /tmp/$test_exchange.md5sum`

    if [ "$src_md5" == "$sink_md5" ]
    then
	echo "Success!"
    else
	echo "Error!"
	retval=1
    fi
done

rm -f $checksum_fifo
rm -f /tmp/$test_exchange.md5sum

exit $retval

