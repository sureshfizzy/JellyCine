# JellyCine webOS Native Build
#
#   make setup-env    — install cross-compiler + download sysroot
#   make              — build the binary
#   make ipk          — package as webOS IPK
#   make deploy       — install on TV via SSH
#   make run          — launch on TV

APP_ID      := com.jellycine.webos
APP_NAME    := jellycine
VERSION     := 1.0.0

TV_IP       ?= 192.168.1.100
TV_USER     ?= root

# Auto-detect toolchain: NDK first, then system cross-compiler
WEBOS_SDK   ?= $(HOME)/webos-ndk/arm-webos-linux-gnueabi_sdk-buildroot
NDK_CC      := $(WEBOS_SDK)/bin/arm-webos-linux-gnueabi-gcc
SYS_CC      := $(shell which arm-linux-gnueabi-gcc 2>/dev/null)
SYSROOT_NDK := $(WEBOS_SDK)/arm-webos-linux-gnueabi/sysroot
SYSROOT_EXT := $(HOME)/webos-ndk/sysroot

ifneq (,$(wildcard $(NDK_CC)))
  CC      := $(NDK_CC)
  STRIP   := $(WEBOS_SDK)/bin/arm-webos-linux-gnueabi-strip
  SYSROOT := $(SYSROOT_NDK)
  EXTRA_LDFLAGS :=
else ifneq (,$(SYS_CC))
  CC      := $(SYS_CC)
  STRIP   := $(shell which arm-linux-gnueabi-strip 2>/dev/null)
  ifneq (,$(wildcard $(SYSROOT_NDK)))
    SYSROOT := $(SYSROOT_NDK)
  else
    SYSROOT := $(SYSROOT_EXT)
  endif
  EXTRA_LDFLAGS := -Wl,-rpath-link,$(SYSROOT)/lib -Wl,--allow-shlib-undefined
else
  CC      := $(error No ARM cross-compiler found. Run: make setup-env)
  STRIP   := true
  SYSROOT := /nonexistent
  EXTRA_LDFLAGS :=
endif

CFLAGS := \
	-march=armv7-a -mtune=cortex-a9 -mfloat-abi=softfp \
	--sysroot=$(SYSROOT) \
	-Isrc \
	-O2 -Wall -Wextra -Wno-unused-parameter \
	-DAPP_ID=\"$(APP_ID)\" \
	-DAPP_VERSION=\"$(VERSION)\"

LDFLAGS := \
	--sysroot=$(SYSROOT) \
	-Wl,--gc-sections \
	$(EXTRA_LDFLAGS)

LIBS := -lSDL2 -lSDL2_ttf -lGLESv2 -lEGL -ldl -lpthread -lm

SRCS := src/main.c src/jellyfin.c src/webos.c \
	src/ui/renderer.c src/ui/text.c src/ui/screen.c src/ui/focus.c
OBJS := $(SRCS:.c=.o)
BIN  := $(APP_NAME)
BUILD_DIR := build

.PHONY: all clean ipk deploy run setup-env

all: $(BIN)

$(BIN): $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $^ $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	rm -f $(OBJS) $(BIN)
	rm -rf $(BUILD_DIR)

ipk: $(BIN)
	@rm -rf $(BUILD_DIR)/ipk
	@mkdir -p $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)
	@mkdir -p $(BUILD_DIR)/ipk/data/usr/palm/packages/$(APP_ID)
	@mkdir -p $(BUILD_DIR)/ipk/control
	cp $(BIN) $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/
	$(STRIP) $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/$(BIN)
	cp appinfo.json $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/
	@echo '{"id":"$(APP_ID)","version":"$(VERSION)","app":"$(APP_ID)"}' \
		> $(BUILD_DIR)/ipk/data/usr/palm/packages/$(APP_ID)/packageinfo.json
	cp ipkroot/ctl/control $(BUILD_DIR)/ipk/control/
	cd $(BUILD_DIR)/ipk && tar czf ../control.tar.gz -C control .
	cd $(BUILD_DIR)/ipk && tar czf ../data.tar.gz -C data .
	echo "2.0" > $(BUILD_DIR)/debian-binary
	@cd $(BUILD_DIR) && \
		IPK=$(APP_ID)_$(VERSION)_arm.ipk && \
		printf '!<arch>\n' > $$IPK && \
		for m in debian-binary control.tar.gz data.tar.gz; do \
			sz=$$(wc -c < $$m); \
			printf '%-16s%-12s%-6s%-6s%-8s%-10s\x60\n' \
				"$$m" "$$(date +%s)" 0 0 100644 "$$sz" >> $$IPK; \
			cat $$m >> $$IPK; \
			if [ $$((sz % 2)) -ne 0 ]; then printf '\n' >> $$IPK; fi; \
		done && \
		echo "Built: $(BUILD_DIR)/$$IPK ($$(wc -c < $$IPK) bytes)"

deploy: ipk
	scp $(BUILD_DIR)/$(APP_ID)_$(VERSION)_arm.ipk $(TV_USER)@$(TV_IP):/tmp/
	ssh $(TV_USER)@$(TV_IP) "opkg install --force-reinstall /tmp/$(APP_ID)_$(VERSION)_arm.ipk"

run:
	ssh $(TV_USER)@$(TV_IP) \
		"luna-send -n 1 luna://com.webos.service.applicationmanager/launch '{\"id\":\"$(APP_ID)\"}'"

# setup-env: install cross-compiler + download webOS sysroot
NDK_REL     := webos-d7ed7ee.6
NDK_SYSROOT_URL := https://github.com/webosbrew/native-toolchain/releases/download/$(NDK_REL)/arm-webos-linux-gnueabi_sdk-buildroot_darwin-x86_64.tar.bz2

setup-env:
	@command -v arm-linux-gnueabi-gcc >/dev/null 2>&1 || { \
		echo "Installing ARM cross-compiler..."; \
		sudo apt-get update -qq && sudo apt-get install -y -qq gcc-arm-linux-gnueabi; }
	@test -d $(SYSROOT_EXT)/usr/include/SDL2 && { echo "Sysroot already present"; } || { \
		echo "Downloading webOS sysroot..."; \
		mkdir -p $(HOME)/webos-ndk && cd $(HOME)/webos-ndk && \
		curl -fL --retry 3 -o ndk.tar.bz2 "$(NDK_SYSROOT_URL)" && \
		tar xjf ndk.tar.bz2 --strip-components=1 \
			"arm-webos-linux-gnueabi_sdk-buildroot/arm-webos-linux-gnueabi/sysroot" && \
		mv arm-webos-linux-gnueabi/sysroot . && \
		rm -rf arm-webos-linux-gnueabi ndk.tar.bz2 && \
		echo "Sysroot ready"; }
	@echo ""
	@echo "Done. Run: make && make ipk"
