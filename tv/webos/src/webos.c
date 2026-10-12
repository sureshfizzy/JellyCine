#include "webos.h"

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

typedef struct LSHandle LSHandle;
typedef unsigned long LSMessageToken;

typedef bool (*ls_register_fn)(const char *, LSHandle **, void *);
typedef bool (*ls_unregister_fn)(LSHandle *, void *);
typedef bool (*ls_call_fn)(LSHandle *, const char *, const char *,
                           void *, void *, LSMessageToken *, void *);

static void *s_luna_lib = NULL;
static ls_register_fn   fn_register   = NULL;
static ls_unregister_fn fn_unregister = NULL;
static ls_call_fn       fn_call       = NULL;
static LSHandle *s_handle = NULL;

bool webos_init(void) {
    s_luna_lib = dlopen("liblunaservice-2.so", RTLD_LAZY);
    if (!s_luna_lib)
        s_luna_lib = dlopen("liblunaservice-2.so.3", RTLD_LAZY);

    if (!s_luna_lib) {
        fprintf(stderr, "Luna Service not available (non-webOS environment?)\n");
        return true;
    }

    fn_register   = dlsym(s_luna_lib, "LSRegister");
    fn_unregister = dlsym(s_luna_lib, "LSUnregister");
    fn_call       = dlsym(s_luna_lib, "LSCall");

    if (fn_register) {
        fn_register(APP_ID, &s_handle, NULL);
    }

    return true;
}

void webos_shutdown(void) {
    if (s_handle && fn_unregister) {
        fn_unregister(s_handle, NULL);
        s_handle = NULL;
    }
    if (s_luna_lib) {
        dlclose(s_luna_lib);
        s_luna_lib = NULL;
    }
}

void webos_toast(const char *message) {
    if (!s_handle || !fn_call) return;

    char payload[512];
    snprintf(payload, sizeof(payload),
        "{\"sourceId\":\"%s\",\"message\":\"%s\"}", APP_ID, message);

    LSMessageToken token = 0;
    fn_call(s_handle,
        "luna://com.webos.notification/createToast",
        payload, NULL, NULL, &token, NULL);
}