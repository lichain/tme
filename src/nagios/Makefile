
NAME        = tme-nagios
VERSION     = 2.5
PKGNAME     = ${NAME}-${VERSION}
SPECNAME    = nagios
OUTPATH     = build

LIBPATH     = lib
VARPATH     = var
CFGPATH     = etc
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

nagios: 
	@ant

rpm: 
	-if [ ! -d ../../output/RPMS ]; then mkdir ../../output/{BUILD,RPMS,SOURCES,SPECS,SRPMS}; fi
	mkdir -p $(RPMPATH)/opt/trendmicro/${NAME}
	cp -f $(OUTPATH)/check-* $(RPMPATH)/opt/trendmicro/${NAME}
	cp -f $(OUTPATH)/tme-*.jar $(RPMPATH)/opt/trendmicro/${NAME}
	cd $(OUTPATH); tar cfz ../../../output/SOURCES/$(PKGNAME).tar.gz $(PKGNAME)
	rm -rf $(RPMPATH)
	export STAMP_VER=$(BUILD_TAG); cat $(NAME).spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > $(NAME)-tmp.spec
	rpmbuild --target noarch --define="_topdir `pwd`/../../output" -bb $(NAME)-tmp.spec
	rm -f $(NAME)-tmp.spec

upload:
	export STAMP_VER=$(BUILD_TAG); curl -T "../../output/RPMS/output/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/TME/TME-2.5/

clean:
	@ant clean
