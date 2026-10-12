# JellyCine webOS Native Build
#
# Requires: webosbrew native-toolchain
#   make setup-env    — prints toolchain download instructions
#   make              — build the binary
#   make ipk          — package as webOS IPK
#   make deploy       — install on TV via SSH
#   make run          — launch on TV

TOOLCHAIN   ?= $(HOME)/.local/share/webosbrew/toolchain
SYSROOT     := $(TOOLCHAIN)/arm-webos-linux-gnueabi/sysroot
CC          := $(TOOLCHAIN)/bin/arm-webos-linux-gnueabi-gcc
STRIP       := $(TOOLCHAIN)/bin/arm-webos-linux-gnueabi-strip

APP_ID      := com.jellycine.webos
APP_NAME    := jellycine
VERSION     := 1.0.0

TV_IP       ?= 192.168.1.100
TV_USER     ?= root

CFLAGS := \
	-march=armv7-a -mtune=cortex-a9 -mfloat-abi=softfp \
	--sysroot=$(SYSROOT) \
	-Isrc \
	-O2 -Wall -Wextra -Wno-unused-parameter \
	-DAPP_ID=\"$(APP_ID)\" \
	-DAPP_VERSION=\"$(VERSION)\"

LDFLAGS := \
	--sysroot=$(SYSROOT) \
	-Wl,--gc-sections

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
	@mkdir -p $(BUILD_DIR)/ipk/control
	cp $(BIN) $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/
	$(STRIP) $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/$(BIN)
	cp appinfo.json $(BUILD_DIR)/ipk/data/usr/palm/applications/$(APP_ID)/
	cp ipkroot/ctl/control $(BUILD_DIR)/ipk/control/
	cd $(BUILD_DIR)/ipk && tar czf ../control.tar.gz -C control .
	cd $(BUILD_DIR)/ipk && tar czf ../data.tar.gz -C data .
	echo "2.0" > $(BUILD_DIR)/debian-binary
	cd $(BUILD_DIR) && ar r $(APP_ID)_$(VERSION).ipk debian-binary control.tar.gz data.tar.gz
	@echo "Built: $(BUILD_DIR)/$(APP_ID)_$(VERSION).ipk"

deploy: ipk
	scp $(BUILD_DIR)/$(APP_ID)_$(VERSION).ipk $(TV_USER)@$(TV_IP):/tmp/
	ssh $(TV_USER)@$(TV_IP) "opkg install --force-reinstall /tmp/$(APP_ID)_$(VERSION).ipk"

run:
	ssh $(TV_USER)@$(TV_IP) \
		"luna-send -n 1 luna://com.webos.service.applicationmanager/launch '{\"id\":\"$(APP_ID)\"}'"

setup-env:
	@echo "Download the webosbrew native toolchain:"
	@echo "  https://github.com/nickel-org/nickel.rs"
	@echo "Extract to: $(TOOLCHAIN)"