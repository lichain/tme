all:

openmq:
	mkdir -p build
	cp openmq4_5-source.zip build
	cd build ; unzip openmq4_5-source.zip
	cp MQ-*.patch build
	cd build ; for patch in `ls MQ-*.patch` ; do patch -p0 < $$patch ; done
	cd build/mq ; ant bootstrap
	cd build/mq ; ant
	mv -f build/mq/dist/mq/lib/imq.jar ../src/common/lib/imq.jar
	mv -f build/mq/dist/mq/lib/jms.jar ../src/common/lib/jms.jar
	mv -f build/mq/dist/mq/lib/imqjmx.jar ../src/common/lib/imqjmx.jar
	mv -f build/mq/dist/mq/lib/imqbroker.jar ../src/broker/lib/imqbroker.jar
	mv -f build/mq/dist/mq/lib/imqutil.jar ../src/broker/lib/imqutil.jar
	mv -f build/mq/dist/mq/lib/imqadmin.jar ../src/broker/lib/imqadmin.jar
	sed -i "s/\$$_classes/\'\/opt\/trend\/tme\/lib\/*\'/g" build/mq/dist/mq/bin/imqcmd
	mv -f build/mq/dist/mq/bin/imqcmd ../src/broker/bin/imqcmd
	mv -f build/mq/dist/mq/lib/imqinit ../src/broker/lib/imqinit

protobuf:
	mkdir -p ../output/works/
	cp protobuf-2.1.0.tar.gz ../output/works
	cd ../output/works ; tar -zxvf protobuf-2.1.0.tar.gz
	cd ../output/works/protobuf-2.1.0 ; ./configure ; make
	
clean:
	rm -rf build
