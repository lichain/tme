# ----------------------------------------------------------------------------
# Global/shared directories that should inherit from top-level makefile.
# In case nothing is inherit from, we still have our defaults that follow O2
# convention.
# ----------------------------------------------------------------------------

TOP_DIR        ?= $(abspath ../..)
TOP_OUTPUT_DIR ?= $(TOP_DIR)/output
ARTIFACTS_DIR  ?= $(TOP_OUTPUT_DIR)/artifacts
RPMBUILD_DIR   ?= $(TOP_OUTPUT_DIR)/rpmbuild
WORKS_DIR      ?= $(TOP_OUTPUT_DIR)/works/mistic


# ----------------------------------------------------------------------------
# Module specific variables
# ----------------------------------------------------------------------------

CODE        = tme-mist-tools
NAME        = tme-mist-tools
VERSION     = 2.5
PKGNAME     = ${NAME}-${VERSION}

BINPATH     = $(WORKS_DIR)/mistic
BUILDPATH   = build
LIBPATH     = lib
CONFPATH    = conf
WEBPATH		= web
RPMPATH     = $(BUILDPATH)/$(PKGNAME)
INSTALLPATH	= /opt/trend/tme

SRCDIR      = src

ifndef CI_TAG
CI_TAG        = $(shell date -u "+%Y%m%dZ")
ifdef JOB_NAME
CI_TAG       += _$(JOB_NAME)
endif
ifdef BUILD_NUMBER
CI_TAG       += _build$(BUILD_NUMBER)
endif
endif

# ----------------------------------------------------------------------------
# Main targets
# ----------------------------------------------------------------------------

#.PHONY: default
default: $(WORKS_DIR)/../protobuf-2.1.0/src/protoc
	mkdir -p $(WORKS_DIR)/
	cd $(WORKS_DIR) ; cmake $(TOP_DIR)/src ; make

$(WORKS_DIR)/../protobuf-2.1.0/src/protoc:
	cd $(TOP_DIR)/3rd_party ; make protobuf

.PHONY: rebuild
rebuild: clean default

.PHONY: release
release: rebuild upload

.PHONY: rpm
rpm: default
	mkdir -p $(RPMBUILD_DIR)/{RPMS,SOURCES,BUILD,SPECS,SRPMS}
	# Create source tarball
	mkdir -p $(RPMPATH)/usr/bin
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cp -f $(BINPATH)/mist-session $(RPMPATH)/usr/bin
	cp -f $(BINPATH)/mist-sink $(RPMPATH)/usr/bin
	cp -f $(BINPATH)/mist-source $(RPMPATH)/usr/bin
	cp -f $(BINPATH)/mist-encode $(RPMPATH)/usr/bin
	cp -f $(BINPATH)/mist-decode $(RPMPATH)/usr/bin
	cp -f $(WORKS_DIR)/../protobuf-2.1.0/src/.libs/libprotobuf.so.4 $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cd $(BUILDPATH); tar cfz $(PKGNAME).tar.gz $(PKGNAME)
	mv $(BUILDPATH)/$(PKGNAME).tar.gz $(RPMBUILD_DIR)/SOURCES
	rm -rf $(RPMPATH) 
	# Create SPEC file with changelog, and build RPM files.
	export STAMP_VER=$(CI_TAG); cat $(NAME).spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > $(NAME)-tmp.spec
	rpmbuild --quiet --target `uname -i` --define="_topdir $(RPMBUILD_DIR)" -ba $(NAME)-tmp.spec ; rm -f $(NAME)-tmp.spec
	# Copy RPM files to artifacts directory
	mkdir -p $(ARTIFACTS_DIR);
	cp -f $(RPMBUILD_DIR)/RPMS/*/$(NAME)-*.rpm $(ARTIFACTS_DIR)/;
	cp -f $(RPMBUILD_DIR)/SRPMS/$(NAME)-*.rpm  $(ARTIFACTS_DIR)/;

.PHONY: clean
clean:
	rm -rf $(WORKS_DIR)

