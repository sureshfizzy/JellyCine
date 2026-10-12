#ifndef JELLYCINE_JELLYFIN_H
#define JELLYCINE_JELLYFIN_H

#include <stdbool.h>

typedef struct JellyfinClient JellyfinClient;

typedef struct {
    char *name;
    char *id;
} JfUser;

typedef struct {
    char *access_token;
    char *server_id;
    JfUser user;
} JfAuthResult;

typedef struct {
    char *id;
    char *name;
    char *type;
    char *collection_type;
} JfLibrary;

typedef struct {
    JfLibrary *items;
    int count;
} JfLibraryList;

JellyfinClient *jf_client_create(const char *server_url);
void jf_client_destroy(JellyfinClient *client);

bool jf_connect(JellyfinClient *client);

/* Auth POST /Users/AuthenticateByName */
bool jf_authenticate(JellyfinClient *client, const char *username,
                     const char *password, JfAuthResult *out);

/* Set token from a previous session */
void jf_set_token(JellyfinClient *client, const char *token,
                  const char *user_id);

/* Library views GET /Users/{userId}/Views */
bool jf_get_libraries(JellyfinClient *client, JfLibraryList *out);

/* Cleanup helpers */
void jf_auth_result_free(JfAuthResult *result);
void jf_library_list_free(JfLibraryList *list);

#endif