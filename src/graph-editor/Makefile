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
VERSION     = 1.0
PKGNAME     = ${NAME}-${VERSION}

BINPATH     = bin
BUILDPATH   = build
LIBPATH     = lib
CONFPATH    = conf
WEBPATH		= web
RPMPATH     = $(BUILDPATH)/$(PKGNAME)
INSTALLPATH	= /opt/trend/tme

SRCDIR      = src

# The CI_TAG represent when and in which CI build the package was built.
# - The RPM release version will be/contain this tag.
# - JOB_NAME and BUILD_NUMBER will be defined automatically if the package
#   is built by Hudson/Jenkins.
# - Hudson/Jenkins job name should not contain '-' character, which is
#   invalid in RPM release version. That is why we should not use BUILD_TAG
#   from Hudson/Jenkins directly.
# - XXX: Is the generated RPM release version conform with GOD standard?
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
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(CONFPATH)
	mkdir -p $(RPMPATH)/$(INSTALLPATH)/$(WEBPATH)
	cp -f $(BINPATH)/graph-editor $(RPMPATH)/$(INSTALLPATH)/$(BINPATH)
	cp -f $(BUILDPATH)/*.jar $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cp -rf $(LIBPATH)/* $(RPMPATH)/$(INSTALLPATH)/$(LIBPATH)
	cp -f $(CONFPATH)/* $(RPMPATH)/$(INSTALLPATH)/$(CONFPATH)
	cp -rf $(WEBPATH)/* $(RPMPATH)/$(INSTALLPATH)/$(WEBPATH)
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

.PHONY: chagelog
changelog:
	export STAMP_VER=$(CI_TAG); rpm -qp --changelog "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" > /tmp/$(NAME)-changelog.txt; curl -T /tmp/$(NAME)-changelog.txt -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/$(CODE)/$(CODE)-$(VERSION)/

.PHONY: rpminfo
rpminfo:
	@export STAMP_VER=$(CI_TAG); echo "==> RPM contents"; rpm -qpl "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm"; echo "==> RPM changelog"; rpm -qp --changelog "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" 

.PHONY: install
install:
	@export STAMP_VER=$(CI_TAG); rpm -ivh "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm"

.PHONY: uninstall
uninstall:
	@rpm -e $(NAME)

.PHONY: upgrade
upgrade:
	@export STAMP_VER=$(CI_TAG); rpm -Uvh --force "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm"

.PHONY: upload
upload:
	export STAMP_VER=$(CI_TAG); curl -T "../../output/RPMS/noarch/$(NAME)-$(VERSION)-$$STAMP_VER.noarch.rpm" -u anonymous:anonymous ftp://wiki.spn.tw.trendnet.org/release/$(CODE)/$(CODE)-$(VERSION)/

.PHONY: clean
clean:
	@ant clean

.PHONY: test
test: clean
	ant coverage;

