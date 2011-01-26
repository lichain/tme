
NAME        = tme-deploy
VERSION     = 1.0
PKGNAME     = ${NAME}-${VERSION}

OUTPATH     = ../../output
RPMPATH     = $(OUTPATH)/$(PKGNAME)

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

deploy: 
	cp -rf src ${PKGNAME}
	-mkdir ${PKGNAME}/rpm
	mkdir -p ${PKGNAME}/QA/mist
	-cp ../../QA/mist/* ${PKGNAME}/QA/mist/
	chmod -R u+w ${PKGNAME}
	tar cvfz ${PKGNAME}-${BUILD_TAG}.tgz ${PKGNAME}
	rm -rf ${PKGNAME}
	mv ${PKGNAME}-${BUILD_TAG}.tgz ${OUTPATH}
