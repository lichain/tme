# ----------------------------------------------------------------------------
# Global/shared directories that should inherit from top-level makefile.
# In case nothing is inherit from, we still have our defaults that follow O2
# convention.
# ----------------------------------------------------------------------------

TOP_DIR        ?= $(abspath ../..)
TOP_OUTPUT_DIR ?= $(TOP_DIR)/output
ARTIFACTS_DIR  ?= $(TOP_OUTPUT_DIR)/artifacts
RPMBUILD_DIR   ?= $(TOP_OUTPUT_DIR)/rpmbuild


# ----------------------------------------------------------------------------
# Module specific variables
# ----------------------------------------------------------------------------

CODE        = tme-broker
NAME        = tme-broker
VERSION     = 2.5
PKGNAME     = ${NAME}-${VERSION}

BINPATH     = bin
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

.PHONY: default
default: 
	@ant

.PHONY: rebuild
rebuild: clean default

.PHONY: release
release: rebuild upload

.PHONY: rpm
rpm: default
	mkdir -p $(RPMBUILD_DIR)/{RPMS,SOURCES,BUILD,SPECS,SRPMS}
	# Create source tarball
	mkdir -p $(RPMPATH)/etc/init.d
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(CONFPATH)
	cp -f $(BINPATH)/tme-broker $(RPMPATH)/etc/init.d
	cp -f $(BINPATH)/broker.sh $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BINPATH)/install_tme-broker.sh $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BINPATH)/remove_tme-broker.sh $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BUILDPATH)/*.jar $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cp -rf $(LIBPATH)/* $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cp -rf $(CONFPATH)/* $(RPMPATH)/$(INSTALLPATH)/$(CONFPATH)
	cd $(BUILDPATH); tar cfz $(PKGNAME).tar.gz $(PKGNAME)
	mv $(BUILDPATH)/$(PKGNAME).tar.gz $(RPMBUILD_DIR)/SOURCES
	rm -rf $(RPMPATH) 
	# Create SPEC file with changelog, and build RPM files.
	export STAMP_VER=$(CI_TAG); cat $(NAME).spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > $(NAME)-tmp.spec
	rpmbuild --quiet --target noarch --define="_topdir $(RPMBUILD_DIR)" -ba $(NAME)-tmp.spec ; rm -f $(NAME)-tmp.spec
	# Copy RPM files to artifacts directory
	mkdir -p $(ARTIFACTS_DIR);
	cp -f $(RPMBUILD_DIR)/RPMS/*/$(NAME)-*.rpm $(ARTIFACTS_DIR)/;
	cp -f $(RPMBUILD_DIR)/SRPMS/$(NAME)-*.rpm  $(ARTIFACTS_DIR)/;

.PHONY: clean
clean:
	@ant clean

