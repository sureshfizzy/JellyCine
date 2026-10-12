#include "jellyfin.h"
#include "webos.h"

#include <SDL2/SDL.h>
#include <GLES2/gl2.h>
#include <signal.h>
#include <stdio.h>
#include <stdbool.h>

static bool s_running = true;

static void on_signal(int sig) {
    (void)sig;
    s_running = false;
}

int main(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);

    fprintf(stdout, "JellyCine %s starting\n", APP_VERSION);

    webos_init();

    /* SDL2 + OpenGL ES 2.0 for the UI surface */
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS) < 0) {
        fprintf(stderr, "SDL_Init: %s\n", SDL_GetError());
        return 1;
    }

    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 2);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0);

    SDL_Window *window = SDL_CreateWindow("JellyCine",
        SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
        1920, 1080, SDL_WINDOW_OPENGL | SDL_WINDOW_FULLSCREEN);

    if (!window) {
        fprintf(stderr, "SDL_CreateWindow: %s\n", SDL_GetError());
        SDL_Quit();
        return 1;
    }

    SDL_GLContext gl = SDL_GL_CreateContext(window);
    SDL_GL_SetSwapInterval(1);

    /* TODO: server URL + credentials from on-screen input or saved prefs */
    JellyfinClient *jf = jf_client_create("http://192.168.1.100:8096");

    if (jf && jf_connect(jf)) {
        webos_toast("Connected to Jellyfin");

        JfAuthResult auth = {0};
        if (jf_authenticate(jf, "demo", "demo", &auth)) {
            fprintf(stdout, "User: %s, Token: %.8s...\n",
                auth.user.name, auth.access_token);

            JfLibraryList libs = {0};
            if (jf_get_libraries(jf, &libs)) {
                fprintf(stdout, "Libraries (%d):\n", libs.count);
                for (int i = 0; i < libs.count; i++) {
                    fprintf(stdout, "  [%s] %s\n",
                        libs.items[i].collection_type ? libs.items[i].collection_type : "?",
                        libs.items[i].name);
                }
                jf_library_list_free(&libs);
            }

            jf_auth_result_free(&auth);
        }
    }

    while (s_running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) s_running = false;
            if (ev.type == SDL_KEYDOWN) {
                switch (ev.key.keysym.sym) {
                case SDLK_ESCAPE:
                case SDLK_AC_BACK:
                    s_running = false;
                    break;
                default:
                    break;
                }
            }
        }

        glClearColor(0.06f, 0.06f, 0.11f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        SDL_GL_SwapWindow(window);
    }

    jf_client_destroy(jf);

    SDL_GL_DeleteContext(gl);
    SDL_DestroyWindow(window);
    SDL_Quit();
    webos_shutdown();

    fprintf(stdout, "JellyCine shutdown\n");
    return 0;
}