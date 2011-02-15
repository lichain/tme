
NAME        = tme-zookeeper
VERSION     = 2.5_3.3.1
PKGNAME     = ${NAME}-${VERSION}

CFGPATH     = etc
OUTPATH     = ../../output
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

rpm: 
	mkdir -p $(RPMPATH)/etc/init.d 
	mkdir -p $(RPMPATH)/usr/share/tme-zookeeper/etc
	tar xfz zookeeper-3.3.1.tar.gz -C $(RPMPATH)
	cp -f build/tme-zookeeperd $(RPMPATH)/etc/init.d
	cp -f build/watchdog-zookeeperd $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/install_zookeeperd.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/remove_zookeeperd.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f build/change_zk_mem.sh $(RPMPATH)/usr/share/tme-zookeeper
	cp -f jmxremote.* $(RPMPATH)/usr/share/tme-zookeeper
	cp -f $(CFGPATH)/log4j.properties $(RPMPATH)/usr/share/tme-zookeeper/etc
	cp -f $(CFGPATH)/tme-zookeeper.cron $(RPMPATH)/usr/share/tme-zookeeper/etc
	cd $(OUTPATH); tar cfz /usr/src/redhat/SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf ${RPMPATH}
	export STAMP_VER=$(BUILD_TAG); cat ${NAME}.spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > ${NAME}-tmp.spec
	rpmbuild --target noarch -bb ${NAME}-tmp.spec
	rm -f ${NAME}-tmp.spec
	mv -f /usr/src/redhat/RPMS/noarch/$(PKGNAME)*.noarch.rpm $(OUTPATH)

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/TME/TME-2.0/
