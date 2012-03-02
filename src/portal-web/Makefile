VERSION=2.5
RELEASE=$(shell date -u "+%Y%m%dZ")

OUTPUT_DIR=$(shell pwd)/output
RPMBUILD_DIR=$(OUTPUT_DIR)/rpmbuild
ARTIFACTS_DIR=$(OUTPUT_DIR)/artifacts
SOURCE_PREP_DIR=$(OUTPUT_DIR)/tme-portal-web-$(VERSION)

.PHONY: rpm
rpm:
	bundle install --local 
	mkdir -p $(RPMBUILD_DIR)/{RPMS,SOURCES,BUILD,SPECS,SRPMS}
	mkdir -p $(ARTIFACTS_DIR)
	mkdir -p $(SOURCE_PREP_DIR)
	git archive --format tar -o $(SOURCE_PREP_DIR)/tme-portal-web.tar master
	cd $(SOURCE_PREP_DIR); tar -xvf tme-portal-web.tar
	rm -rf $(SOURCE_PREP_DIR)/tme-portal-web.tar
	rm -rf $(SOURCE_PREP_DIR)/vendor
	rm -rf $(SOURCE_PREP_DIR)/tests
	cp -rf lib/ruby $(SOURCE_PREP_DIR)/lib
	cd $(OUTPUT_DIR); tar -zcvf $(RPMBUILD_DIR)/SOURCES/tme-portal-web-$(VERSION).tar.gz tme-portal-web-$(VERSION)
	rpmbuild --target noarch --define "version $(VERSION)" --define "release $(RELEASE)" --define="_topdir $(RPMBUILD_DIR)" -ba tme-portal-web.spec
	cp $(RPMBUILD_DIR)/RPMS/noarch/* $(ARTIFACTS_DIR)
	cp $(RPMBUILD_DIR)/SRPMS/* $(ARTIFACTS_DIR)

.PHONY: clean
clean:
	rm -rf lib/ruby
	rm -rf $(OUTPUT_DIR)
