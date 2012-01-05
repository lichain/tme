openmq:
	mkdir -p build
	cp openmq4_5-source.zip build
	cd build ; unzip openmq4_5-source.zip
	patch build/mq/src/share/java/com/sun/messaging/jmq/util/lists/NFLPriorityFifoSet.java < MQ-136.patch
	cd build/mq ; ant bootstrap
	cd build/mq ; ant
	mv -f build/mq/dist/mq/lib/imq.jar ../src/common/lib/imq.jar
	mv -f build/mq/dist/mq/lib/jms.jar ../src/common/lib/jms.jar
	mv -f build/mq/dist/mq/lib/imqjmx.jar ../src/common/lib/imqjmx.jar
	mv -f build/mq/dist/mq/lib/imqbroker.jar ../src/broker/lib/imqbroker.jar
	mv -f build/mq/dist/mq/lib/imqutil.jar ../src/broker/lib/imqutil.jar
	
clean:
	rm -rf build
