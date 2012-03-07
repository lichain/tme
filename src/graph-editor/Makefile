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

CODE        = tme-graph-editor
NAME        = tme-graph-editor
VERSION     = 2.5
PKGNAME     = ${NAME}-${VERSION}

BINPATH     = bin
BUILDPATH   = build
LIBPATH     = lib
CONFPATH    = conf
WEBPATH		= web
RPMPATH     = $(BUILDPATH)/$(PKGNAME)
INSTALLPATH	= /opt/trend/tme
BUILD_PREFIX    ?=

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

DEBBUILD_DIR   := $(TOP_OUTPUT_DIR)/deb/$(PKGNAME)-$(CI_TAG)_all

# ----------------------------------------------------------------------------
# Main targets
# ----------------------------------------------------------------------------

.PHONY: default
default: 
	@ant

.PHONY: install
install: default
	mkdir -p $(BUILD_PREFIX)/etc/init.d
	mkdir -p $(BUILD_PREFIX)/$(INSTALLPATH)/$(BINPATH)
	mkdir -p $(BUILD_PREFIX)/$(INSTALLPATH)/$(LIBPATH)
	mkdir -p $(BUILD_PREFIX)/$(INSTALLPATH)/$(CONFPATH)
	mkdir -p $(BUILD_PREFIX)/$(INSTALLPATH)/$(WEBPATH)
	cp -f $(BINPATH)/tme-graph-editor $(BUILD_PREFIX)/etc/init.d
	cp -f $(BINPATH)/graph-editor.sh $(BUILD_PREFIX)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BINPATH)/install_tme-graph-editor.sh $(BUILD_PREFIX)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BINPATH)/remove_tme-graph-editor.sh $(BUILD_PREFIX)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BUILDPATH)/*.jar $(BUILD_PREFIX)/$(INSTALLPATH)/$(LIBPATH)
	cp -rf $(LIBPATH)/* $(BUILD_PREFIX)/$(INSTALLPATH)/$(LIBPATH)
	cp -rf $(CONFPATH)/* $(BUILD_PREFIX)/$(INSTALLPATH)/$(CONFPATH)
	cp -rf $(WEBPATH)/* $(BUILD_PREFIX)/$(INSTALLPATH)/$(WEBPATH)

.PHONY: rebuild
rebuild: clean default

.PHONY: release
release: rebuild upload

.PHONY: rpm
rpm: default
	mkdir -p $(RPMBUILD_DIR)/{RPMS,SOURCES,BUILD,SPECS,SRPMS}
	# Create source tarball
	rm -rf $(RPMPATH)
	BUILD_PREFIX=$(RPMPATH) make install
	cd $(BUILDPATH); tar cfz $(PKGNAME).tar.gz $(PKGNAME)
	mv $(BUILDPATH)/$(PKGNAME).tar.gz $(RPMBUILD_DIR)/SOURCES
	rm -rf $(RPMPATH) 
	# Create SPEC file with changelog, and build RPM files.
	export STAMP_VER=$(CI_TAG); cat pkg/rpm/$(NAME).spec | sed -e "s/#RELEASE_VER#/$$STAMP_VER/" -e "s/#MAJOR_VER#/$(VERSION)/" > $(NAME)-tmp.spec
	rpmbuild --quiet --target noarch --define="_topdir $(RPMBUILD_DIR)" -ba $(NAME)-tmp.spec ; rm -f $(NAME)-tmp.spec
	# Copy RPM files to artifacts directory
	mkdir -p $(ARTIFACTS_DIR);
	cp -f $(RPMBUILD_DIR)/RPMS/*/$(NAME)-*.rpm $(ARTIFACTS_DIR)/;
	cp -f $(RPMBUILD_DIR)/SRPMS/$(NAME)-*.rpm  $(ARTIFACTS_DIR)/;

.PHONY: clean
clean:
	@ant clean

