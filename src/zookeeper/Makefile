
NAME        = tme-zookeeper
VERSION     = 2.5_3.3.2
PKGNAME     = ${NAME}-${VERSION}

CFGPATH     = etc
OUTPATH     = ../../output
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

rpm: 
	-if [ ! -d ../../output/RPMS ]; then mkdir ../../output/{BUILD,RPMS,SOURCES,SPECS,SRPMS}; fi
	mkdir -p $(RPMPATH)/etc/init.d 
	mkdir -p $(RPMPATH)/usr/share/tme-zookeeper/etc
	tar xfz zookeeper-3.3.2.tar.gz -C $(RPMPATH)
	cp -f build/tme-zookeeperd $(RPMPATH)/etc/init.d
	cp -f build/watchdog-zookeeperd $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/install_zookeeperd.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/remove_zookeeperd.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/change_zk_mem.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f jmxremote.* $(RPMPATH)/usr/share/tme-zookeeper
	cp -f $(CFGPATH)/log4j.properties $(RPMPATH)/usr/share/tme-zookeeper/etc
	cp -f $(CFGPATH)/tme-zookeeper.cron $(RPMPATH)/usr/share/tme-zookeeper/etc
	cd $(OUTPATH); tar cfz SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf ${RPMPATH}
	export STAMP_VER=$(BUILD_TAG); cat ${NAME}.spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > ${NAME}-tmp.spec
	rpmbuild --target noarch --define="_topdir `pwd`/../../output" -bb ${NAME}-tmp.spec
	rm -f ${NAME}-tmp.spec

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/dailybuild/TME/TME-2.5/
