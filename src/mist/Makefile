
NAME        = tme-mist
VERSION     = 2.5
PKGNAME     = ${NAME}-${VERSION}

OUTPATH     = build
OUT_W32     = windows
LIBPATH     = lib
CFGPATH     = etc
RPMPATH     = $(OUTPATH)/$(PKGNAME)

SRCDIR      = src

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

mist: 
	@export STAMP_VER=${VERSION}-${BUILD_TAG}; echo 'package com.trendmicro.mist; public class Version { public static String getVersion() { return "MIST_VERSION"; } }' | sed -e "s/MIST_VERSION/$$STAMP_VER/" > $(SRCDIR)/com/trendmicro/mist/Version.java
	@ant
	@ant mistClient 

unit_test:
	@ant test

doc:
	@ant doc

rpm: 
	-if [ ! -d ../../output/RPMS ]; then mkdir ../../output/{BUILD,RPMS,SOURCES,SPECS,SRPMS}; fi
	mkdir -p $(RPMPATH)/etc/init.d
	mkdir -p $(RPMPATH)/usr/bin
	mkdir -p $(RPMPATH)/usr/share/mist/bin
	mkdir -p $(RPMPATH)/usr/share/mist/etc
	mkdir -p $(RPMPATH)/usr/share/mist/lib
	cp -f $(OUTPATH)/mistd  $(RPMPATH)/etc/init.d
	cp -f $(OUTPATH)/mist-session $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-source $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-sink $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-decode $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-encode $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-forwarder $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-line-gen $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/mist-broker $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/test_mistd $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/alert-spyd $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/test_autogoc $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/test_tls $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/mon_mistd $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/watchdog-mistd $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/install_mistd.sh $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/remove_mistd.sh $(RPMPATH)/usr/share/mist/bin
	cp -f $(CFGPATH)/tme-mist.cron $(RPMPATH)/usr/share/mist/etc
	cp -f $(OUTPATH)/tme-bridge $(RPMPATH)/etc/init.d
	cp -f $(OUTPATH)/bridge-console $(RPMPATH)/usr/bin
	cp -f $(OUTPATH)/install_bridge.sh $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/remove_bridge.sh $(RPMPATH)/usr/share/mist/bin
	cp -f $(OUTPATH)/watchdog-bridge $(RPMPATH)/usr/share/mist/bin
	cp -f $(CFGPATH)/tme-bridge.cron $(RPMPATH)/usr/share/mist/etc
	cp -f $(CFGPATH)/mistd.log4j $(RPMPATH)/usr/share/mist/etc
	cp -f $(CFGPATH)/mistd.properties $(RPMPATH)/usr/share/mist/etc
	cp -rf $(LIBPATH)/* $(RPMPATH)/usr/share/mist/lib
	cd $(OUTPATH); tar cfz ../../../output/SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf $(RPMPATH) 
	export STAMP_VER=$(BUILD_TAG); cat $(NAME).spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > $(NAME)-tmp.spec
	rpmbuild --target noarch --define="_topdir `pwd`/../../output" -bb $(NAME)-tmp.spec
	rm -f $(NAME)-tmp.spec

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/dailybuild/TME/TME-2.5/

install:
	export STAMP_VER=$(BUILD_TAG); rpm -ivh ../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm

clean:
	@ant clean
