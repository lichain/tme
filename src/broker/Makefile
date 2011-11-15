
NAME        = tme-broker
VERSION     = 2.5_4.5.1
PKGNAME     = ${NAME}-${VERSION}

OUTPATH     = ../../output
BINPATH     = build
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

rpm: 
	-if [ ! -d ../../output/RPMS ]; then mkdir ../../output/{BUILD,RPMS,SOURCES,SPECS,SRPMS}; fi
	mkdir -p $(RPMPATH)/etc/init.d
	mkdir -p $(RPMPATH)/usr/share/mist/bin
	mkdir -p $(RPMPATH)/usr/share/tme-broker
	cp openmq4_5_1-binary-Linux_X86.zip $(RPMPATH)/
	cd $(RPMPATH) ; unzip openmq4_5_1-binary-Linux_X86.zip

	cp -f $(BINPATH)/tme-brokerd $(RPMPATH)/etc/init.d
	cp -f $(BINPATH)/tme-spyd  $(RPMPATH)/etc/init.d
	cp -f $(BINPATH)/watchdog-spyd  $(RPMPATH)/usr/share/mist/bin
	cp -f $(BINPATH)/install_brokerd.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f $(BINPATH)/remove_brokerd.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f $(BINPATH)/change_broker_mem.sh  $(RPMPATH)/usr/share/tme-broker
	cp -f tme-spyd.cron $(RPMPATH)/usr/share/tme-broker
	cp -f jmxremote.*  $(RPMPATH)/usr/share/tme-broker
	cp -f config.properties $(RPMPATH)/
	cd $(OUTPATH); tar cfz SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf $(RPMPATH)
	export STAMP_VER=$(BUILD_TAG); cat ${NAME}.spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > ${NAME}-tmp.spec
	rpmbuild --target noarch --define="_topdir `pwd`/../../output" -bb ${NAME}-tmp.spec
	rm -f ${NAME}-tmp.spec

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/RPMS/output/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/TME/TME-2.0/

