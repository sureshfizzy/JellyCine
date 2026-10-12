#include "jellyfin.h"

#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void CURL;
typedef int CURLcode;
#define CURLE_OK 0
#define CURLOPT_URL       10002
#define CURLOPT_POSTFIELDS 10015
#define CURLOPT_HTTPHEADER 10023
#define CURLOPT_WRITEFUNCTION 20011
#define CURLOPT_WRITEDATA 10001

typedef void *(*curl_easy_init_fn)(void);
typedef CURLcode (*curl_easy_setopt_fn)(CURL *, int, ...);
typedef CURLcode (*curl_easy_perform_fn)(CURL *);
typedef void (*curl_easy_cleanup_fn)(CURL *);
typedef struct curl_slist *(*curl_slist_append_fn)(struct curl_slist *, const char *);
typedef void (*curl_slist_free_all_fn)(struct curl_slist *);

static void *s_curl_lib = NULL;
static curl_easy_init_fn     fn_easy_init     = NULL;
static curl_easy_setopt_fn   fn_easy_setopt   = NULL;
static curl_easy_perform_fn  fn_easy_perform  = NULL;
static curl_easy_cleanup_fn  fn_easy_cleanup  = NULL;
static curl_slist_append_fn  fn_slist_append  = NULL;
static curl_slist_free_all_fn fn_slist_free   = NULL;

typedef struct {
    char *data;
    size_t len;
    size_t cap;
} Buffer;

static void buf_init(Buffer *b) {
    b->cap = 4096;
    b->data = malloc(b->cap);
    b->data[0] = '\0';
    b->len = 0;
}

static void buf_free(Buffer *b) {
    free(b->data);
    b->data = NULL;
    b->len = b->cap = 0;
}

static size_t write_cb(char *ptr, size_t size, size_t nmemb, void *userdata) {
    Buffer *b = userdata;
    size_t total = size * nmemb;
    if (b->len + total >= b->cap) {
        b->cap = (b->len + total) * 2;
        b->data = realloc(b->data, b->cap);
    }
    memcpy(b->data + b->len, ptr, total);
    b->len += total;
    b->data[b->len] = '\0';
    return total;
}

static char *json_string_value(const char *json, const char *key) {
    char pattern[256];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char *p = strstr(json, pattern);
    if (!p) return NULL;

    p += strlen(pattern);
    while (*p == ' ' || *p == ':' || *p == '\t') p++;
    if (*p != '"') return NULL;
    p++;

    const char *end = p;
    while (*end && *end != '"') {
        if (*end == '\\') end++;
        end++;
    }

    size_t len = end - p;
    char *val = malloc(len + 1);
    memcpy(val, p, len);
    val[len] = '\0';
    return val;
}

struct JellyfinClient {
    char *server_url;
    char *access_token;
    char *user_id;
    char *device_id;
};

static bool load_curl(void) {
    if (s_curl_lib) return true;

    s_curl_lib = dlopen("libcurl.so", RTLD_LAZY);
    if (!s_curl_lib) s_curl_lib = dlopen("libcurl.so.4", RTLD_LAZY);
    if (!s_curl_lib) {
        fprintf(stderr, "libcurl not available: %s\n", dlerror());
        return false;
    }

    fn_easy_init    = dlsym(s_curl_lib, "curl_easy_init");
    fn_easy_setopt  = dlsym(s_curl_lib, "curl_easy_setopt");
    fn_easy_perform = dlsym(s_curl_lib, "curl_easy_perform");
    fn_easy_cleanup = dlsym(s_curl_lib, "curl_easy_cleanup");
    fn_slist_append = dlsym(s_curl_lib, "curl_slist_append");
    fn_slist_free   = dlsym(s_curl_lib, "curl_slist_free_all");

    if (!fn_easy_init || !fn_easy_perform) {
        fprintf(stderr, "Failed to resolve curl symbols\n");
        dlclose(s_curl_lib);
        s_curl_lib = NULL;
        return false;
    }

    return true;
}

static char *build_url(const JellyfinClient *c, const char *endpoint) {
    size_t len = strlen(c->server_url) + strlen(endpoint) + 2;
    char *url = malloc(len);
    snprintf(url, len, "%s/%s", c->server_url, endpoint);
    return url;
}

static char *build_auth_header(const JellyfinClient *c) {
    char header[1024];
    if (c->access_token) {
        snprintf(header, sizeof(header),
            "Authorization: MediaBrowser Client=\"JellyCine\", "
            "Device=\"webOS TV\", DeviceId=\"%s\", Version=\"%s\", "
            "Token=\"%s\"",
            c->device_id, APP_VERSION, c->access_token);
    } else {
        snprintf(header, sizeof(header),
            "Authorization: MediaBrowser Client=\"JellyCine\", "
            "Device=\"webOS TV\", DeviceId=\"%s\", Version=\"%s\"",
            c->device_id, APP_VERSION);
    }
    return strdup(header);
}

static bool http_request(const JellyfinClient *c, const char *url,
                         const char *post_body, Buffer *response) {
    CURL *curl = fn_easy_init();
    if (!curl) return false;

    buf_init(response);

    struct curl_slist *headers = NULL;
    char *auth = build_auth_header(c);
    headers = fn_slist_append(headers, auth);
    headers = fn_slist_append(headers, "Content-Type: application/json");

    fn_easy_setopt(curl, CURLOPT_URL, url);
    fn_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    fn_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    fn_easy_setopt(curl, CURLOPT_WRITEDATA, response);

    if (post_body) {
        fn_easy_setopt(curl, CURLOPT_POSTFIELDS, post_body);
    }

    CURLcode res = fn_easy_perform(curl);

    fn_slist_free(headers);
    fn_easy_cleanup(curl);
    free(auth);

    if (res != CURLE_OK) {
        buf_free(response);
        return false;
    }

    return true;
}

JellyfinClient *jf_client_create(const char *server_url) {
    if (!load_curl()) return NULL;

    JellyfinClient *c = calloc(1, sizeof(JellyfinClient));

    size_t url_len = strlen(server_url);
    c->server_url = strdup(server_url);
    if (url_len > 0 && c->server_url[url_len - 1] == '/') {
        c->server_url[url_len - 1] = '\0';
    }

    c->device_id = strdup("jellycine-webos-001");
    return c;
}

void jf_client_destroy(JellyfinClient *client) {
    if (!client) return;
    free(client->server_url);
    free(client->access_token);
    free(client->user_id);
    free(client->device_id);
    free(client);
}

bool jf_connect(JellyfinClient *client) {
    char *url = build_url(client, "System/Info/Public");
    Buffer resp;

    bool ok = http_request(client, url, NULL, &resp);
    free(url);

    if (ok) {
        char *name = json_string_value(resp.data, "ServerName");
        if (name) {
            fprintf(stdout, "Connected to server: %s\n", name);
            free(name);
        }
        buf_free(&resp);
    }

    return ok;
}

bool jf_authenticate(JellyfinClient *client, const char *username,
                     const char *password, JfAuthResult *out) {
    char *url = build_url(client, "Users/AuthenticateByName");
    char body[512];
    snprintf(body, sizeof(body),
        "{\"Username\":\"%s\",\"Pw\":\"%s\"}", username, password);

    Buffer resp;
    bool ok = http_request(client, url, body, &resp);
    free(url);

    if (!ok) return false;

    memset(out, 0, sizeof(*out));
    out->access_token = json_string_value(resp.data, "AccessToken");
    out->server_id = json_string_value(resp.data, "ServerId");

    const char *user_block = strstr(resp.data, "\"User\"");
    if (user_block) {
        out->user.name = json_string_value(user_block, "Name");
        out->user.id = json_string_value(user_block, "Id");
    }

    buf_free(&resp);

    if (!out->access_token) return false;

    free(client->access_token);
    client->access_token = strdup(out->access_token);
    free(client->user_id);
    client->user_id = out->user.id ? strdup(out->user.id) : NULL;

    fprintf(stdout, "Authenticated as: %s\n", out->user.name ? out->user.name : "unknown");
    return true;
}

void jf_set_token(JellyfinClient *client, const char *token,
                  const char *user_id) {
    free(client->access_token);
    client->access_token = strdup(token);
    free(client->user_id);
    client->user_id = strdup(user_id);
}

bool jf_get_libraries(JellyfinClient *client, JfLibraryList *out) {
    if (!client->user_id) return false;

    char endpoint[256];
    snprintf(endpoint, sizeof(endpoint), "Users/%s/Views", client->user_id);
    char *url = build_url(client, endpoint);

    Buffer resp;
    bool ok = http_request(client, url, NULL, &resp);
    free(url);

    if (!ok) return false;

    memset(out, 0, sizeof(*out));

    int capacity = 16;
    out->items = calloc(capacity, sizeof(JfLibrary));

    const char *p = strstr(resp.data, "\"Items\"");
    if (!p) { buf_free(&resp); return false; }

    p = strchr(p, '[');
    if (!p) { buf_free(&resp); return false; }

    while ((p = strchr(p, '{')) != NULL) {
        const char *block_end = strchr(p, '}');
        if (!block_end) break;

        size_t block_len = block_end - p + 1;
        char *block = malloc(block_len + 1);
        memcpy(block, p, block_len);
        block[block_len] = '\0';

        if (out->count >= capacity) {
            capacity *= 2;
            out->items = realloc(out->items, capacity * sizeof(JfLibrary));
        }

        JfLibrary *lib = &out->items[out->count];
        lib->id = json_string_value(block, "Id");
        lib->name = json_string_value(block, "Name");
        lib->type = json_string_value(block, "Type");
        lib->collection_type = json_string_value(block, "CollectionType");

        if (lib->id) out->count++;
        free(block);
        p = block_end + 1;
    }

    buf_free(&resp);
    return true;
}

void jf_auth_result_free(JfAuthResult *result) {
    if (!result) return;
    free(result->access_token);
    free(result->server_id);
    free(result->user.name);
    free(result->user.id);
    memset(result, 0, sizeof(*result));
}

void jf_library_list_free(JfLibraryList *list) {
    if (!list) return;
    for (int i = 0; i < list->count; i++) {
        free(list->items[i].id);
        free(list->items[i].name);
        free(list->items[i].type);
        free(list->items[i].collection_type);
    }
    free(list->items);
    memset(list, 0, sizeof(*list));
}