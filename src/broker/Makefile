
NAME        = tme-broker
VERSION     = 2.0u1_4.4
PKGNAME     = ${NAME}-${VERSION}

OUTPATH     = ../../output
BINPATH     = build
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

rpm: 
	mkdir -p $(RPMPATH)/etc/init.d
	mkdir -p $(RPMPATH)/usr/share/mist/bin
	mkdir -p $(RPMPATH)/usr/share/tme-broker
	tar xfz openmq4_4-binary.tgz -C $(RPMPATH)/
	cp -f $(BINPATH)/tme-brokerd $(RPMPATH)/etc/init.d
	cp -f $(BINPATH)/tme-spyd  $(RPMPATH)/etc/init.d
	cp -f $(BINPATH)/install_spyd.sh  $(RPMPATH)/usr/share/mist/bin
	cp -f $(BINPATH)/remove_spyd.sh  $(RPMPATH)/usr/share/mist/bin
	cp -f $(BINPATH)/watchdog-spyd  $(RPMPATH)/usr/share/mist/bin
	cp -f $(BINPATH)/install_brokerd.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f $(BINPATH)/remove_brokerd.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f $(BINPATH)/change_broker_mem.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f tme-spyd.cron $(RPMPATH)/usr/share/tme-broker
	cp -f jmxremote.*  $(RPMPATH)/usr/share/tme-broker
	cp -f config.properties $(RPMPATH)/
	cd $(OUTPATH); tar cfz /usr/src/redhat/SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf $(RPMPATH)
	export STAMP_VER=$(BUILD_TAG); cat ${NAME}.spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > ${NAME}-tmp.spec
	rpmbuild --target noarch -bb ${NAME}-tmp.spec
	rm -f ${NAME}-tmp.spec
	mv -f /usr/src/redhat/RPMS/noarch/$(PKGNAME)*.noarch.rpm $(OUTPATH)

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/TME/TME-2.0/

openmq:
	@if [ "$$ANT_HOME" == "" ]; then echo "ANT_HOME is empty. Please use 'make openmq ANT_HOME=/path/to/ant'"; exit 1; fi
	unzip ../../3rd_party/openmq4_4-source.zip
	patch -p1 -i mq_issue68.patch
	cd mq; $$ANT_HOME/bin/ant bootstrap ; $$ANT_HOME/bin/ant
	cd mq/dist; tar cfz ../../openmq4_4-binary.tgz mq/
	rm -rf mq
